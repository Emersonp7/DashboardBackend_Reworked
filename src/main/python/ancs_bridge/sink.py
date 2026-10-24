"""The only module that knows HTTP exists.

Isolated on purpose: this holds the backend-restart replay contract, which is
the one piece of bridge behaviour that can be tested end to end with no BLE
hardware at all. Point an ApiSink at a running notifications service, restart
the service, and `check_backend()` must replay the mirror.

Stdlib only. The bridge runs as root on the system interpreter (it needs the
apt-provided dbus/gi), so it must never acquire a pip dependency.
"""

from __future__ import annotations

import json
import logging
import urllib.error
import urllib.request
from collections import OrderedDict
from typing import Any

log = logging.getLogger(__name__)

INGEST_PATH = "/notifications/ingest"
DEVICE_PATH = "/notifications/device"
STATE_PATH = "/notifications"

TIMEOUT_SECONDS = 4


class ApiSink:
    """Posts to the dashboard, and repairs it after a restart.

    Keeps a mirror of everything it has told the backend about, so a backend
    restart can be repaired without touching the BLE link. iOS only replays its
    backlog when WE re-subscribe, which a backend restart does not cause.
    """

    def __init__(self, api: str) -> None:
        self.api = api.rstrip("/")
        self.sent: "OrderedDict[str, dict]" = OrderedDict()
        self.backend_boot: str | None = None

    # -- outbound --------------------------------------------------------

    def post(self, payload: dict[str, Any], path: str = INGEST_PATH) -> None:
        body = json.dumps(payload).encode()
        req = urllib.request.Request(
            f"{self.api}{path}",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS).read()
        except (urllib.error.URLError, OSError) as e:
            # The dashboard being down must never take the BLE link with it.
            log.warning("post failed (%s); dropping %s", e, payload.get("uid"))

    def remember(self, payload: dict[str, Any]) -> None:
        """Track what the backend should be holding, so we can rebuild it."""
        uid = payload.get("uid")
        if not uid:
            return
        if payload.get("event") == "removed":
            self.sent.pop(uid, None)
        else:
            self.sent[uid] = payload

    # -- restart repair --------------------------------------------------

    def check_backend(self, on_replay=None) -> bool:
        """Detect a backend restart and re-send everything we know about.

        Returns True so it can be used directly as a GLib timeout callback.
        """
        try:
            with urllib.request.urlopen(
                f"{self.api}{STATE_PATH}", timeout=TIMEOUT_SECONDS
            ) as r:
                state = json.loads(r.read().decode())
        except (urllib.error.URLError, OSError, ValueError):
            return True  # backend down; try again on the next tick

        boot = state.get("boot_id")
        if not boot:
            return True
        if self.backend_boot is None:
            # First sighting: adopt it silently. Replaying here would double up
            # whatever iOS has just finished replaying to us.
            self.backend_boot = boot
            return True
        if boot == self.backend_boot:
            return True

        log.info("backend restarted; replaying %d notification(s)", len(self.sent))
        self.backend_boot = boot
        for payload in list(self.sent.values()):
            # Mark the replay pre-existing so nothing re-announces itself.
            self.post(dict(payload, pre_existing=True))
        if on_replay is not None:
            on_replay()
        return True
