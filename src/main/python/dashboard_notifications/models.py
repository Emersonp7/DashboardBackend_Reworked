"""Wire models.

The two request shapes are dictated by the ANCS bridge and cannot drift: it
posts plain JSON with these exact field names. The response shapes are ours,
except that ``Snapshot`` must keep ``boot_id`` — the bridge polls it to detect
that this process restarted (see ``store.NotificationStore``).
"""

from __future__ import annotations

from pydantic import BaseModel, Field


class IngestIn(BaseModel):
    """One notification as the bridge decodes it off the ANCS link.

    Everything but ``uid`` has a default because the bridge posts twice for a
    normal notification: once with just the metadata it gets from the
    Notification Source packet, then again with the text once the Control Point
    round trip completes. An ``IncomingCall`` leans on that hard — it is posted
    metadata-only immediately so a ringing phone shows up while it is still
    ringing.
    """

    uid: str
    event: str = "added"  # added | modified | removed
    category: str = "Other"  # ANCS CategoryID, e.g. IncomingCall, Social
    app: str = ""  # bundle id, e.g. com.apple.MobileSMS
    title: str = ""  # usually the sender
    subtitle: str = ""
    message: str = ""
    date: str = ""  # ANCS date, "YYYYMMDDTHHMMSS"
    pre_existing: bool = False  # part of the backlog replayed on connect
    silent: bool = False
    important: bool = False


class DeviceIn(BaseModel):
    """What the bridge can learn about the phone itself over BLE.

    ``battery`` comes from the standard Battery Service (0x180F); ``model`` and
    ``firmware`` from Device Information (0x180A). All optional and merged, not
    replaced — a bare ``{"connected": false}`` heartbeat must not wipe the last
    known battery level, because the UI shows it as "62% last seen".
    """

    connected: bool = True
    name: str | None = None
    battery: int | None = None  # percent
    model: str | None = None
    firmware: str | None = None


class NotificationOut(BaseModel):
    uid: str
    category: str
    app: str
    title: str
    subtitle: str
    message: str
    date: str
    pre_existing: bool
    silent: bool
    important: bool
    #: When we first saw this uid. Preserved across a `modified` event so
    #: filling in the text later does not make it look new.
    received_ts: float


class DeviceOut(BaseModel):
    connected: bool = False
    name: str | None = None
    battery: int | None = None
    model: str | None = None
    firmware: str | None = None
    updated_ts: float | None = None
    #: Set when the bridge has not reported in for `device_stale_after`.
    #: Computed per response, never stored.
    stale: bool = False


class Snapshot(BaseModel):
    """Everything the dashboard needs in one poll."""

    #: New on every process start. The bridge watches this: if it changes, the
    #: bridge re-posts everything it believes we should be holding.
    boot_id: str
    #: Bumps on every mutation so a client can cheaply detect one. Note it does
    #: NOT bump when `suppressed` flips, so it must never be a sole memo key.
    seq: int
    #: Length of the list actually returned — zero while withheld.
    total: int
    linked: bool
    #: True when the list is being withheld because the phone is out of reach.
    suppressed: bool
    #: How many are waiting behind the link. Equals `total` when linked.
    held: int
    bridge_seen_ts: float
    device_seen_ts: float
    device: DeviceOut
    notifications: list[NotificationOut]


class Ack(BaseModel):
    status: str = "ok"
    seq: int
    total: int


class Health(BaseModel):
    service: str = "notifications"
    status: str = "ok"


class DeviceAck(BaseModel):
    status: str = "ok"
    device: DeviceOut = Field(...)
