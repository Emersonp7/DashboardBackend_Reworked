"""iPhone notification bridge: ANCS over BLE -> the dashboard's HTTP API.

Runs as root on the SYSTEM interpreter, because dbus-python and PyGObject are
the apt packages python3-dbus / python3-gi. This package must therefore stay
stdlib-only (plus dbus/gi) -- never add a pip dependency to it.
"""
