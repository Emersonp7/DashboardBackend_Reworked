"""Composition: wire the sink, the advertiser and the ANCS session together,
install the GLib timers, and own the main loop.

Timer intervals are frozen; each exists for a reason recorded in the original.
"""

from __future__ import annotations

import logging
import signal

import dbus
import dbus.service
from gi.repository import GLib

from .bluez import Advertisement, Advertiser, Agent, configure_adapter, disable_classic
from .constants import (
    ADAPTER_IFACE,
    ADV_PATH,
    AGENT_MGR_IFACE,
    AGENT_PATH,
    BLUEZ,
    HEARTBEAT_SECONDS,
    LE_ADV_MGR_IFACE,
    OM_IFACE,
    PROPS_IFACE,
)
from .session import AncsSession
from .sink import ApiSink

log = logging.getLogger(__name__)

#: Re-attach if the phone wanders off and comes back.
WATCHDOG_SECONDS = 20
#: Repair the dashboard after it restarts (boot_id change).
BACKEND_CHECK_SECONDS = 15
#: One-shot first device read, so the battery shows up without waiting 120s.
FIRST_DEVICE_READ_SECONDS = 5


class Bridge:
    def __init__(self, api: str, name: str) -> None:
        self.bus = dbus.SystemBus()
        self.loop = GLib.MainLoop()
        self.name = name
        self.adapter_path: str | None = None

        self.sink = ApiSink(api)
        self.advertiser: Advertiser | None = None
        self.session: AncsSession | None = None
        self.agent: Agent | None = None
        self.adv: Advertisement | None = None

    def objects(self):
        return dbus.Interface(
            self.bus.get_object(BLUEZ, "/"), OM_IFACE
        ).GetManagedObjects()

    def start(self) -> bool:
        for path, ifaces in self.objects().items():
            if ADAPTER_IFACE in ifaces and LE_ADV_MGR_IFACE in ifaces:
                self.adapter_path = path
                break
        if not self.adapter_path:
            log.error("no BLE-capable adapter")
            return False

        configure_adapter(self.bus, self.adapter_path)
        disable_classic(self.adapter_path)

        self.agent = Agent(self.bus, AGENT_PATH)
        mgr = dbus.Interface(
            self.bus.get_object(BLUEZ, "/org/bluez"), AGENT_MGR_IFACE
        )
        try:
            mgr.RegisterAgent(AGENT_PATH, "NoInputNoOutput")
            mgr.RequestDefaultAgent(AGENT_PATH)
        except dbus.exceptions.DBusException:
            # Already registered by a previous run of this process' path.
            pass

        self.advertiser = Advertiser(self.bus, self.adapter_path)
        self.session = AncsSession(self.bus, self.sink, self.advertiser)

        self.adv = Advertisement(self.bus, ADV_PATH, self.name)
        # Deliberately NOT advertising here. run() calls scan_existing()
        # immediately below, and that decides from the adapter's real
        # connection state. Advertising unconditionally first raced the two
        # calls: RegisterAdvertisement is async (it carries reply/error
        # handlers), so a phone that was already connected made scan_existing
        # call the synchronous UnregisterAdvertisement against a registration
        # that had not landed yet. That failed with "Does Not Exist", leaving
        # the flag and BlueZ disagreeing -- an advertisement live for the whole
        # session while every later set(False) early-returned.
        return True

    def check_backend(self) -> bool:
        # On a detected restart, re-push device state too so `linked` recovers
        # without waiting out the 120s heartbeat.
        return self.sink.check_backend(on_replay=self.session.report_device)

    def run(self) -> int:
        assert self.sink is not None
        self.bus.add_signal_receiver(
            self._on_props,
            dbus_interface=PROPS_IFACE,
            signal_name="PropertiesChanged",
            path_keyword="path",
        )
        if not self.start():
            return 2

        session = self.session
        assert session is not None

        session.scan_existing()
        GLib.timeout_add_seconds(WATCHDOG_SECONDS, session.scan_existing)
        # report_device returns True to stay scheduled, so the "first read"
        # timer must be one-shot or it keeps firing every 5s forever -- which it
        # was doing, re-reading battery over GATT twenty-four times a minute.
        GLib.timeout_add_seconds(
            FIRST_DEVICE_READ_SECONDS, lambda: (session.report_device(), False)[1]
        )
        GLib.timeout_add_seconds(HEARTBEAT_SECONDS, session.report_device)
        GLib.timeout_add_seconds(BACKEND_CHECK_SECONDS, self.check_backend)

        for sig in (signal.SIGINT, signal.SIGTERM):
            # A graceful exit closes the D-Bus connection, which makes BlueZ
            # drop the advertisement and the agent -- that is what makes a
            # systemd handover to another bridge instance clean.
            GLib.unix_signal_add(
                GLib.PRIORITY_DEFAULT, sig, lambda: (self.loop.quit(), False)[1]
            )
        self.loop.run()
        return 0

    def _on_props(self, iface, changed, invalidated, path=None):
        if self.session is not None:
            self.session.on_props(iface, changed, invalidated, path=path)
