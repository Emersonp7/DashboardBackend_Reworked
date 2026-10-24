"""The notification store: all of this service's mutable state, in one object.

Nothing is persisted. These are ephemeral by nature — the phone is the system
of record, and iOS replays its whole active set whenever the bridge re-subscribes.

Recovery works in both directions:

* If the **bridge** restarts, iOS replays everything on re-subscribe (flagged
  ``pre_existing`` so the UI does not re-announce it).
* If **this service** restarts, the bridge stays connected and iOS has no reason
  to replay anything, so this store would sit empty forever. ``boot_id`` exists
  for exactly that: it changes on every process start, and the bridge re-posts
  its own mirror when it notices a new one.

Drop ``boot_id`` and the phone page stays blank after every API restart until
the user happens to walk out of Bluetooth range and back.
"""

from __future__ import annotations

import logging
import threading
import time
import uuid
from collections import OrderedDict
from typing import Callable

from .models import Ack, DeviceIn, DeviceOut, IngestIn, NotificationOut, Snapshot

log = logging.getLogger(__name__)


class NotificationStore:
    """Thread-safe view of what is currently on the phone.

    The lock is held only for dictionary work, never across I/O, so the FastAPI
    handlers can stay ``async`` without risk of blocking the event loop.
    """

    def __init__(
        self,
        *,
        max_active: int = 60,
        device_stale_after: float = 420.0,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self._max_active = max_active
        self._device_stale_after = device_stale_after
        self._clock = clock

        self._lock = threading.Lock()
        self._active: "OrderedDict[str, NotificationOut]" = OrderedDict()
        self._seq = 0
        self._last_ingest = 0.0
        self._device = DeviceOut(connected=False)

        self.boot_id = uuid.uuid4().hex

    # -- mutations -------------------------------------------------------

    def ingest(self, incoming: IngestIn) -> Ack:
        with self._lock:
            # Bumped for every ingest including `removed`. This is reported as
            # `bridge_seen_ts` but is deliberately NOT used for liveness: a
            # phone with nothing to say is still a connected phone, and keying
            # the link state off notification traffic reported a perfectly
            # healthy link as down after fifteen quiet minutes.
            self._last_ingest = self._clock()

            if incoming.event == "removed":
                self._active.pop(incoming.uid, None)
                return self._ack_locked()

            existing = self._active.get(incoming.uid)
            record = NotificationOut(
                uid=incoming.uid,
                category=incoming.category,
                app=incoming.app,
                title=incoming.title,
                subtitle=incoming.subtitle,
                message=incoming.message,
                date=incoming.date,
                pre_existing=incoming.pre_existing,
                silent=incoming.silent,
                important=incoming.important,
                # Preserved across a `modified` event, so a client does not
                # re-announce something it has already shown. The bridge posts
                # twice for every notification, so without this every one of
                # them would announce itself a second time.
                received_ts=existing.received_ts if existing else self._clock(),
            )

            self._active[incoming.uid] = record
            self._active.move_to_end(incoming.uid)

            # Insertion order is therefore *last touched*, so eviction drops the
            # least recently touched. Note the snapshot sorts by `received_ts`
            # (first seen) instead — two different orderings, both deliberate.
            while len(self._active) > self._max_active:
                dropped, _ = self._active.popitem(last=False)
                log.debug("evicted %s (over %d)", dropped, self._max_active)

            return self._ack_locked()

    def dismiss(self, uid: str) -> Ack:
        """Drop one locally.

        This does not touch the phone. ANCS can perform a real dismiss, but that
        is a separate action the bridge would have to send.

        Raises ``KeyError`` when the uid is unknown.
        """
        with self._lock:
            if uid not in self._active:
                raise KeyError(uid)
            self._active.pop(uid)
            return self._ack_locked()

    def clear(self) -> Ack:
        with self._lock:
            self._active.clear()
            return self._ack_locked()

    def set_device(self, info: DeviceIn) -> DeviceOut:
        with self._lock:
            # Merge, never replace: a `{"connected": false}` heartbeat must
            # leave the last known name/battery/model intact so the UI can say
            # "62% last seen" rather than dropping to nothing.
            patch = info.model_dump(exclude_none=True)
            merged = self._device.model_dump()
            merged.update(patch)
            merged["updated_ts"] = self._clock()
            self._device = DeviceOut(**merged)
            return self._device

    # -- reads -----------------------------------------------------------

    def snapshot(self) -> Snapshot:
        with self._lock:
            items = sorted(
                self._active.values(), key=lambda r: r.received_ts, reverse=True
            )
            device = self._device.model_copy()
            seq = self._seq
            last_ingest = self._last_ingest

        # Liveness comes from the bridge's own heartbeat, never from
        # notification traffic. `stale` is computed per response and never
        # stored, so the link recovers the moment a beat arrives.
        beat = device.updated_ts or 0.0
        device.stale = not beat or (self._clock() - beat) > self._device_stale_after

        linked = bool(device.connected) and not device.stale

        # With the phone away, what we hold is a snapshot of a moment that has
        # since moved on: things may have been read, replied to or dismissed on
        # the phone and we would never hear about it. Showing that is worse than
        # showing nothing, so the list is WITHHELD rather than discarded — it
        # comes back intact the moment the link does, and iOS replays the truth
        # on re-subscribe.
        held = len(items)
        if not linked:
            items = []

        return Snapshot(
            boot_id=self.boot_id,
            seq=seq,
            total=len(items),
            linked=linked,
            suppressed=not linked,
            held=held,
            bridge_seen_ts=last_ingest,
            device_seen_ts=beat,
            device=device,
            notifications=items,
        )

    def device(self) -> DeviceOut:
        with self._lock:
            return self._device.model_copy()

    # -- internals -------------------------------------------------------

    def _ack_locked(self) -> Ack:
        """Bump `seq` and describe the result. Caller must hold the lock."""
        self._seq += 1
        return Ack(status="ok", seq=self._seq, total=len(self._active))
