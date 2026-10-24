"""Entry point: ``sudo python3 -m ancs_bridge --api http://127.0.0.1:8001/api``"""

from __future__ import annotations

import argparse
import logging
import sys

import dbus.mainloop.glib

from .bridge import Bridge
from .constants import DEFAULT_API, DEFAULT_NAME


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", default=DEFAULT_API)
    parser.add_argument("--name", default=DEFAULT_NAME)
    parser.add_argument("--log-level", default="INFO")
    args = parser.parse_args()

    # stderr and unbuffered: journald goes quiet for minutes at a time otherwise.
    logging.basicConfig(
        level=getattr(logging, args.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)-7s %(message)s",
        datefmt="%H:%M:%S",
        stream=sys.stderr,
    )

    # Must happen before anything constructs a SystemBus.
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    sys.exit(Bridge(args.api, args.name).run())


if __name__ == "__main__":
    main()
