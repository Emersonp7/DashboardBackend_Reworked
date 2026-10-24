"""The ANCS state machine.

FROZEN. `subscribed` / `probing` / `probes` / `stalled` / `inflight` / `pending`
/ `meta` / `queue` / `chrcs` are one state machine with cross-cutting
invariants, and the original's comments record three separate occasions where a
wedged flag made the bridge sit connected and permanently silent. The bodies
here are ported unchanged apart from logging, type hints, and routing output
through `self.sink` instead of inline HTTP.

If you are tempted to simplify something in this file, the test to apply is:
"if this line were subtly wrong, would the symptom be a BLE or ANCS failure?"
If yes, leave it alone.
"""

from __future__ import annotations

import logging
import struct
from collections import deque

import dbus
from gi.repository import GLib

from .constants import (
    ANCS_CONTROL_POINT,
    ANCS_DATA_SOURCE,
    ANCS_NOTIF_SOURCE,
    ANCS_SVC,
    ATTR_APP,
    ATTR_DATE,
    ATTR_MESSAGE,
    ATTR_SUBTITLE,
    ATTR_TITLE,
    BATTERY_LEVEL,
    BLUEZ,
    CATEGORY,
    DEVICE_IFACE,
    EVENT_ID,
    FIRMWARE_REV,
    FLAG_IMPORTANT,
    FLAG_PRE_EXISTING,
    FLAG_SILENT,
    GATT_CHRC_IFACE,
    GATT_SERVICE_IFACE,
    MAXLEN,
    MODEL_NUMBER,
    NEEDS_MAXLEN,
    REQUEST_GAP_MS,
    REQUESTED,
)
from .sink import DEVICE_PATH

log = logging.getLogger(__name__)


class AncsSession:
    """Owns the GATT attachment to one phone and decodes ANCS off it."""

    def __init__(self, bus, sink, advertiser) -> None:
        self.bus = bus
        self.sink = sink
        self.advertiser = advertiser

        self.chrcs: dict[str, str] = {}
        self.pending: dict[int, bytearray] = {}
        self.meta: dict[int, dict] = {}
        self.queue: deque[int] = deque()
        self.inflight: int | None = None
        self.subscribed = False
        self.probing = False
        self.probes = 0
        self.stalled = 0

    # -- dbus helpers ----------------------------------------------------

    def objects(self):
        from .constants import OM_IFACE

        return dbus.Interface(
            self.bus.get_object(BLUEZ, "/"), OM_IFACE
        ).GetManagedObjects()

    def on_props(self, iface, changed, invalidated, path=None):
        if iface == DEVICE_IFACE:
            # `is False` never matched: D-Bus hands back dbus.Boolean(0), which
            # equals False but is not the False singleton. The disconnect branch
            # therefore never ran, `subscribed` stayed True across every drop,
            # and the reconnect found start_probing() already "done" -- the bridge
            # went permanently deaf after the first time the phone wandered off.
            if "Connected" in changed:
                if bool(changed["Connected"]):
                    log.info("phone connected: %s", path)
                    self.advertiser.set(False)
                    self.start_probing()
                else:
                    log.info("phone disconnected; will re-attach when it returns")
                    self.detach()
                    self.advertiser.set(True)
            if changed.get("ServicesResolved") or changed.get("Paired"):
                self.start_probing()
        elif iface == GATT_CHRC_IFACE and "Value" in changed:
            self.on_value(path, bytes(bytearray(changed["Value"])))

    def detach(self) -> None:
        """Forget the subscription so the next connection re-establishes it."""
        self.subscribed = False
        self.probing = False
        self.probes = 0
        self.stalled = 0
        self.chrcs.clear()
        self.pending.clear()
        self.meta.clear()
        self.queue.clear()
        self.inflight = None

    def notifying(self) -> bool:
        """Ask BlueZ whether we are genuinely subscribed.

        Internal flags drift; this is the ground truth. The watchdog compares
        against it so a desync repairs itself instead of persisting silently.
        """
        try:
            for _p, i in self.objects().items():
                c = i.get(GATT_CHRC_IFACE)
                if c and str(c.get("UUID", "")).lower() == ANCS_NOTIF_SOURCE:
                    return bool(c.get("Notifying"))
        except Exception:
            pass
        return False

    # -- attaching -------------------------------------------------------

    def scan_existing(self) -> bool:
        """Watchdog. Runs on a timer and is the thing that guarantees we end up
        attached: if the phone is connected but we are not subscribed, re-arm
        the probe unconditionally rather than trusting `probing` to be accurate.
        A wedged flag used to mean the bridge sat connected and silent forever.
        """
        try:
            connected = False
            for _path, ifaces in self.objects().items():
                d = ifaces.get(DEVICE_IFACE)
                if d and d.get("Connected"):
                    connected = True
                    break
            if not connected:
                # Nothing to attach to. Make sure we are not holding a
                # subscription we no longer have, or the reconnect is a no-op.
                if self.subscribed:
                    log.info("phone gone; dropping stale subscription")
                    self.detach()
                self.advertiser.set(True)
                return True

            self.advertiser.set(False)

            # Believe BlueZ over our own bookkeeping.
            if self.subscribed and not self.notifying():
                log.info("subscription lost while connected; re-attaching")
                self.detach()

            if self.subscribed:
                self.stalled = 0
                return True

            if self.probing:
                self.stalled += 1
                if self.stalled < 3:  # give an in-flight probe a chance
                    return True
                log.info("probe appears stalled; re-arming")
            self.stalled = 0
            self.probing = False
            self.start_probing()
        except Exception as e:
            log.warning("watchdog error: %s", e)
        return True

    def start_probing(self) -> None:
        if self.probing or self.subscribed:
            return
        self.probing = True
        GLib.timeout_add_seconds(2, self.locate)

    def locate(self) -> bool:
        """One attempt at attaching to ANCS. Returns True to be retried.

        Everything here is wrapped: an exception escaping a GLib timeout removes
        the timer for good, which would leave `probing` stuck True and
        `start_probing` returning early forever -- a silent, permanent stop with
        the phone still connected. That is exactly how this failed before.
        """
        try:
            if self.subscribed:
                self.probing = False
                return False
            self.probes += 1
            objs = self.objects()
            svc = next(
                (
                    p
                    for p, i in objs.items()
                    if i.get(GATT_SERVICE_IFACE)
                    and str(i[GATT_SERVICE_IFACE].get("UUID", "")).lower() == ANCS_SVC
                ),
                None,
            )
            if not svc:
                if self.probes % 15 == 1:
                    log.info("ANCS not visible yet (probe %d)", self.probes)
                return True
            self.chrcs = {
                str(i[GATT_CHRC_IFACE].get("UUID", "")).lower(): p
                for p, i in objs.items()
                if i.get(GATT_CHRC_IFACE) and p.startswith(svc)
            }
            found = {ANCS_NOTIF_SOURCE, ANCS_CONTROL_POINT, ANCS_DATA_SOURCE} & set(
                self.chrcs
            )
            if len(found) < 3:
                if self.probes % 15 == 1:
                    log.info(
                        "ANCS present, %d/3 characteristics (probe %d)",
                        len(found),
                        self.probes,
                    )
                return True
            # DATA_SOURCE before NOTIF_SOURCE: subscribe to the answer channel
            # before the question channel, or the first backlog replay's
            # attributes arrive with nobody listening.
            for uuid in (ANCS_DATA_SOURCE, ANCS_NOTIF_SOURCE):
                try:
                    dbus.Interface(
                        self.bus.get_object(BLUEZ, self.chrcs[uuid]), GATT_CHRC_IFACE
                    ).StartNotify()
                except dbus.exceptions.DBusException as e:
                    if "Already" not in str(e):
                        log.warning(
                            "subscribe failed on %s: %s", uuid[:8], e.get_dbus_message()
                        )
                        return True
            self.subscribed = True
            self.probing = False
            self.probes = 0
            log.info("subscribed to ANCS; streaming notifications")
            return False
        except Exception as e:
            log.warning("probe error: %s", e)
            self.probing = False  # let the watchdog re-arm us
            return False

    # -- ancs ------------------------------------------------------------

    def on_value(self, path, data: bytes) -> None:
        if path == self.chrcs.get(ANCS_NOTIF_SOURCE):
            self.on_notification(data)
        elif path == self.chrcs.get(ANCS_DATA_SOURCE):
            self.on_attributes(data)

    def on_notification(self, data: bytes) -> None:
        if len(data) < 8:
            return
        event, flags, cat, _count = data[0], data[1], data[2], data[3]
        uid = struct.unpack_from("<I", data, 4)[0]
        kind = EVENT_ID.get(event, "added")
        meta = {
            "uid": str(uid),
            "event": kind,
            "category": CATEGORY.get(cat, "Other"),
            "pre_existing": bool(flags & FLAG_PRE_EXISTING),
            "silent": bool(flags & FLAG_SILENT),
            "important": bool(flags & FLAG_IMPORTANT),
        }
        if kind == "removed":
            self.sink.remember(meta)
            self.sink.post(meta)
            return
        self.meta[uid] = meta

        if meta["category"] == "IncomingCall":
            # A ringing phone is live for seconds. Post what we already know
            # immediately so it can be shown now, rather than after a Control
            # Point round trip that may be queued behind a backlog replay -- by
            # which time the call has been answered and the notification pulled.
            # The attribute fetch still runs and updates the same uid in place.
            self.sink.remember(meta)
            self.sink.post(meta)
            self.queue.appendleft(uid)
            self._pump_now()  # jump the pacing gap
            return

        self.queue.append(uid)
        self.pump()

    def pump(self) -> None:
        # Only one GATT write may be outstanding; BlueZ returns "In Progress".
        if self.inflight is not None or not self.queue:
            return
        # Pace the queue. On subscribe iOS replays its whole backlog at once,
        # and answering each one costs a Control Point write plus a fragmented
        # multi-packet reply. Draining that as fast as replies arrive buries the
        # link in ATT traffic seconds after it comes up, which is when it kept
        # dropping. A short gap between requests keeps the radio breathing.
        GLib.timeout_add(REQUEST_GAP_MS, self._pump_now)

    def _pump_now(self) -> bool:
        if self.inflight is not None or not self.queue:
            return False
        self.inflight = self.queue.popleft()
        self.request(self.inflight)
        GLib.timeout_add_seconds(3, self.expire, self.inflight)
        return False

    def expire(self, uid: int) -> bool:
        if self.inflight == uid:
            self.pending.pop(uid, None)
            # Also drop the metadata: once `pending` has no entry a late reply
            # is ignored anyway, and a re-queued uid overwrites this. Without
            # it, every timed-out uid leaked a `meta` entry for the session.
            self.meta.pop(uid, None)
            self.inflight = None
            self.pump()
        return False

    def request(self, uid: int) -> None:
        cp = self.chrcs.get(ANCS_CONTROL_POINT)
        if not cp:
            self.inflight = None
            return
        payload = bytearray([0]) + struct.pack("<I", uid)
        for aid in REQUESTED:
            payload.append(aid)
            if aid in NEEDS_MAXLEN:
                payload += struct.pack("<H", MAXLEN)
        self.pending[uid] = bytearray()
        try:
            dbus.Interface(
                self.bus.get_object(BLUEZ, cp), GATT_CHRC_IFACE
            ).WriteValue(
                dbus.Array([dbus.Byte(b) for b in payload], signature="y"),
                dbus.Dictionary({"type": dbus.String("request")}, signature="sv"),
            )
        except dbus.exceptions.DBusException as e:
            log.warning("attribute request failed: %s", e.get_dbus_message())
            self.pending.pop(uid, None)
            self.inflight = None
            GLib.timeout_add(50, lambda: (self.pump(), False)[1])

    def on_attributes(self, data: bytes) -> None:
        # The 5-byte header (command id + uid) appears only on the FIRST
        # fragment of a response; later fragments append raw. This three-branch
        # dispatch is the fiddliest code in the bridge -- leave it alone.
        if len(data) >= 5:
            uid = struct.unpack_from("<I", data, 1)[0]
            if uid in self.pending and not self.pending[uid]:
                self.pending[uid] += data[5:]
            elif self.inflight in self.pending:
                self.pending[self.inflight] += data
        elif self.inflight in self.pending:
            self.pending[self.inflight] += data

        for uid, buf in list(self.pending.items()):
            attrs, i = {}, 0
            while i + 3 <= len(buf):
                aid = buf[i]
                ln = struct.unpack_from("<H", buf, i + 1)[0]
                if i + 3 + ln > len(buf):
                    break
                attrs[aid] = buf[i + 3 : i + 3 + ln].decode("utf-8", "replace")
                i += 3 + ln
            if len(attrs) >= len(REQUESTED):
                del self.pending[uid]
                meta = self.meta.pop(uid, {"uid": str(uid), "event": "added"})
                meta.update(
                    {
                        "app": attrs.get(ATTR_APP, ""),
                        "title": attrs.get(ATTR_TITLE, ""),
                        "subtitle": attrs.get(ATTR_SUBTITLE, ""),
                        "message": attrs.get(ATTR_MESSAGE, ""),
                        "date": attrs.get(ATTR_DATE, ""),
                    }
                )
                self.sink.remember(meta)
                self.sink.post(meta)
                log.info("%-14s %r", meta["category"], meta.get("title", ""))
                if self.inflight == uid:
                    self.inflight = None
                    self.pump()

    # -- device state ----------------------------------------------------

    def read_chrc(self, uuid: str):
        objs = self.objects()
        for path, ifaces in objs.items():
            c = ifaces.get(GATT_CHRC_IFACE)
            if c and str(c.get("UUID", "")).lower() == uuid:
                try:
                    val = dbus.Interface(
                        self.bus.get_object(BLUEZ, path), GATT_CHRC_IFACE
                    ).ReadValue(dbus.Dictionary({}, signature="sv"))
                    return bytes(bytearray(val))
                except dbus.exceptions.DBusException:
                    return None
        return None

    def report_device(self) -> bool:
        """Battery and identity, refreshed periodically. Best effort: these are
        optional extras and must never disturb the notification path."""
        info: dict = {"connected": False}
        try:
            for _p, ifaces in self.objects().items():
                d = ifaces.get(DEVICE_IFACE)
                if d and d.get("Connected"):
                    info["connected"] = True
                    info["name"] = str(d.get("Alias") or "iPhone")
                    break
            if info["connected"]:
                b = self.read_chrc(BATTERY_LEVEL)
                if b:
                    info["battery"] = int(b[0])
                m = self.read_chrc(MODEL_NUMBER)
                if m:
                    info["model"] = m.decode("utf-8", "replace").strip("\x00")
                f = self.read_chrc(FIRMWARE_REV)
                if f:
                    info["firmware"] = f.decode("utf-8", "replace").strip("\x00")
        except Exception as e:
            log.warning("device probe failed: %s", e)
        self.sink.post(info, DEVICE_PATH)
        return True
