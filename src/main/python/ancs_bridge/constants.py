"""Protocol and D-Bus literals.

FROZEN. Every value here is dictated by Apple's ANCS specification or by BlueZ.
Nothing in this file may be "tidied" — a changed UUID, mask, offset or interval
is a silent protocol failure, not a style issue.
"""

BLUEZ = "org.bluez"
ADAPTER_IFACE = "org.bluez.Adapter1"
DEVICE_IFACE = "org.bluez.Device1"
GATT_CHRC_IFACE = "org.bluez.GattCharacteristic1"
GATT_SERVICE_IFACE = "org.bluez.GattService1"
LE_ADV_MGR_IFACE = "org.bluez.LEAdvertisingManager1"
AGENT_MGR_IFACE = "org.bluez.AgentManager1"
PROPS_IFACE = "org.freedesktop.DBus.Properties"
OM_IFACE = "org.freedesktop.DBus.ObjectManager"

ANCS_SVC = "7905f431-b5ce-4e99-a40f-4b1e122d00d0"
ANCS_NOTIF_SOURCE = "9fbf120d-6301-42d9-8c58-25e699a21dbd"
ANCS_CONTROL_POINT = "69d1d8f3-45e1-49a8-9821-9bbdfdaad9d9"
ANCS_DATA_SOURCE = "22eac6e9-24d6-4bb5-be44-b36ace7c7bfb"

# Standard services the iPhone also exposes to a bonded peer. Battery is the
# useful one -- ANCS itself carries no device state.
BATTERY_SVC = "0000180f-0000-1000-8000-00805f9b34fb"
BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"
DEVINFO_SVC = "0000180a-0000-1000-8000-00805f9b34fb"
MODEL_NUMBER = "00002a24-0000-1000-8000-00805f9b34fb"
FIRMWARE_REV = "00002a26-0000-1000-8000-00805f9b34fb"

# How often to re-read device state. The backend calls the link down after
# DEVICE_STALE_AFTER (420s), so this must stay comfortably inside that.
HEARTBEAT_SECONDS = 120

ADV_PATH = "/hud/ancs/bridge/advertisement0"
AGENT_PATH = "/hud/ancs/bridge/agent0"

EVENT_ID = {0: "added", 1: "modified", 2: "removed"}
CATEGORY = {
    0: "Other", 1: "IncomingCall", 2: "MissedCall", 3: "Voicemail",
    4: "Social", 5: "Schedule", 6: "Email", 7: "News",
    8: "HealthAndFitness", 9: "BusinessAndFinance", 10: "Location",
    11: "Entertainment",
}
ATTR_APP, ATTR_TITLE, ATTR_SUBTITLE, ATTR_MESSAGE, ATTR_DATE = 0, 1, 2, 3, 5
REQUESTED = [ATTR_APP, ATTR_TITLE, ATTR_SUBTITLE, ATTR_MESSAGE, ATTR_DATE]
NEEDS_MAXLEN = {ATTR_TITLE, ATTR_SUBTITLE, ATTR_MESSAGE}
# Each text attribute is fetched up to this length, fragmented across ~20-byte
# ATT packets. 160 x3 meant roughly 25 packets per notification; the dashboard
# truncates to two lines anyway, so the extra bytes only cost airtime.
MAXLEN = 96
# Gap between attribute requests, so a backlog replay trickles instead of
# flooding the link the moment it comes up.
REQUEST_GAP_MS = 120

#: Flag masks on the Notification Source packet.
FLAG_SILENT = 0x01
FLAG_IMPORTANT = 0x02
FLAG_PRE_EXISTING = 0x04

#: Where the notifications API lives. The bridge concatenates this with
#: "/notifications/..." so it must include the "/api" prefix.
DEFAULT_API = "http://127.0.0.1:8001/api"
DEFAULT_NAME = "Dashboard HUD"
