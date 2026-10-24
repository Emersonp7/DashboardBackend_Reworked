"""BlueZ plumbing: the solicitation advertisement, the pairing agent, and
adapter setup.

The class bodies here are ported verbatim from the original bridge. The one
deliberate behaviour change is in `Advertiser.set` — see the comment there.
"""

from __future__ import annotations

import logging
import subprocess

import dbus
import dbus.service

from .constants import (
    ADAPTER_IFACE,
    ADV_PATH,
    ANCS_SVC,
    BLUEZ,
    LE_ADV_MGR_IFACE,
    PROPS_IFACE,
)

log = logging.getLogger(__name__)


class Advertisement(dbus.service.Object):
    """The LE advertisement that *solicits* ANCS.

    Soliciting the service is what makes iOS offer its Notification Center to
    us; without it the phone has no reason to expose ANCS to this peer.
    """

    def __init__(self, bus, path, name):
        # Set before super().__init__: exporting the object can make BlueZ call
        # GetAll immediately, and props() reads both of these.
        self.path, self.name = path, name
        super().__init__(bus, path)

    def props(self):
        return {
            "Type": dbus.String("peripheral"),
            "SolicitUUIDs": dbus.Array([ANCS_SVC], signature="s"),
            "LocalName": dbus.String(self.name),
            "Includes": dbus.Array(["tx-power"], signature="s"),
            "Discoverable": dbus.Boolean(True),
        }

    @dbus.service.method(PROPS_IFACE, in_signature="ss", out_signature="v")
    def Get(self, interface, prop):
        return self.props()[prop]

    @dbus.service.method(PROPS_IFACE, in_signature="s", out_signature="a{sv}")
    def GetAll(self, interface):
        return self.props()

    @dbus.service.method("org.bluez.LEAdvertisement1", in_signature="", out_signature="")
    def Release(self):
        pass


class Agent(dbus.service.Object):
    """NoInputNoOutput pairing agent: accept everything, prompt for nothing."""

    @dbus.service.method("org.bluez.Agent1", in_signature="", out_signature="")
    def Release(self): pass

    @dbus.service.method("org.bluez.Agent1", in_signature="o", out_signature="")
    def RequestAuthorization(self, device): pass

    @dbus.service.method("org.bluez.Agent1", in_signature="os", out_signature="")
    def AuthorizeService(self, device, uuid): pass

    @dbus.service.method("org.bluez.Agent1", in_signature="ou", out_signature="")
    def RequestConfirmation(self, device, passkey): pass

    @dbus.service.method("org.bluez.Agent1", in_signature="o", out_signature="s")
    def RequestPinCode(self, device): return "0000"

    @dbus.service.method("org.bluez.Agent1", in_signature="o", out_signature="u")
    def RequestPasskey(self, device): return dbus.UInt32(0)

    @dbus.service.method("org.bluez.Agent1", in_signature="", out_signature="")
    def Cancel(self): pass


class Advertiser:
    """Owns the advertise on/off state for one adapter."""

    def __init__(self, bus, adapter_path: str) -> None:
        self.bus = bus
        self.adapter_path = adapter_path
        self.advertising = False

    def set(self, on: bool) -> None:
        """Advertise only when there is nothing connected.

        Continuing to broadcast during a session is wasted airtime on a radio
        the connection is already competing for, and the solicitation has
        nothing left to solicit.

        The flag is set from the *outcome*, not optimistically. The original
        assigned `self.advertising = on` unconditionally after calling
        RegisterAdvertisement -- but that call passes reply/error handlers, so
        dbus-python runs it asynchronously and it never raises. A failed
        registration therefore left `advertising = True` with nothing actually
        advertising, and because this method early-returns when the flag already
        matches, every later attempt became a no-op: the phone could never find
        the panel again. The watchdog could not rescue it either, since its
        remedy *is* this method. Same dead end in reverse if Unregister raised.
        This is the same stuck-flag family as the `subscribed` and `probing`
        bugs the original's comments record.
        """
        if on == self.advertising:
            return
        mgr = dbus.Interface(
            self.bus.get_object(BLUEZ, self.adapter_path), LE_ADV_MGR_IFACE
        )
        try:
            if on:
                def failed(error):
                    # Let the watchdog try again rather than latching True.
                    self.advertising = False
                    log.warning("advertise failed: %s", error)

                self.advertising = True
                mgr.RegisterAdvertisement(
                    ADV_PATH,
                    dbus.Dictionary({}, signature="sv"),
                    reply_handler=lambda: log.info("advertising"),
                    error_handler=failed,
                )
            else:
                mgr.UnregisterAdvertisement(ADV_PATH)
                self.advertising = False
                log.info("connected; advertising paused")
        except dbus.exceptions.DBusException as e:
            self.advertising = False
            message = e.get_dbus_message()
            # Unregistering something BlueZ does not have is the state we
            # wanted anyway, so it is not worth a warning.
            if not on and "Does Not Exist" in message:
                log.debug("advertisement already absent")
            else:
                log.warning("advertise toggle failed: %s", message)


def disable_classic(adapter_path: str) -> None:
    """Turn off BR/EDR page and inquiry scan, leaving LE untouched.

    ANCS is pure BLE, but the adapter still accepted classic connections --
    and with the a2dp/avrcp plugins disabled (so the panel does not pose as
    a speaker) there was nothing behind the L2CAP channels iOS opened.
    BlueZ answered with a Disconnection Request and iOS tore down the entire
    link: captured as "Remote User Terminated Connection (0x13)" about
    thirty seconds into every session.

    ControllerMode=le in main.conf is the tidier switch but this BlueZ build
    ignores it, so drop the classic surface directly. LE advertising is a
    separate mechanism and is unaffected.
    """
    hci = (adapter_path or "").rsplit("/", 1)[-1] or "hci0"
    try:
        subprocess.run(
            ["hciconfig", hci, "noscan"], check=True, capture_output=True, timeout=5
        )
        log.info("%s: BR/EDR scanning disabled (LE only)", hci)
    except (subprocess.SubprocessError, OSError) as e:
        log.warning("could not disable BR/EDR scanning: %s", e)


def configure_adapter(bus, adapter_path: str) -> None:
    """Power on, stay pairable and discoverable forever."""
    props = dbus.Interface(bus.get_object(BLUEZ, adapter_path), PROPS_IFACE)
    props.Set(ADAPTER_IFACE, "Powered", dbus.Boolean(True))
    props.Set(ADAPTER_IFACE, "Pairable", dbus.Boolean(True))
    # BlueZ expires discoverability after 180s by default, which is wrong for
    # a fixed accessory that must stay reachable to its phone indefinitely.
    props.Set(ADAPTER_IFACE, "DiscoverableTimeout", dbus.UInt32(0))
    props.Set(ADAPTER_IFACE, "PairableTimeout", dbus.UInt32(0))
    props.Set(ADAPTER_IFACE, "Discoverable", dbus.Boolean(True))
