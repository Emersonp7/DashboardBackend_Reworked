#!/usr/bin/env python3
"""Drive the notifications API with a realistic sequence, no phone required.

Exercises every shape the UI has to handle: a stacking conversation, a second
sender that must *not* stack with it, a brand-titled app whose two different
bodies must split into separate stacks, an incoming call, a burst, and the
phone walking out of range and back.

    python3 scripts/seed_notifications.py                 # full sequence
    python3 scripts/seed_notifications.py --scenario away # just the withheld state
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.request

DEFAULT_API = "http://127.0.0.1:8001/api"


def post(api: str, path: str, payload: dict) -> None:
    req = urllib.request.Request(
        f"{api}{path}",
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    urllib.request.urlopen(req, timeout=4).read()


def notify(api: str, uid: str, *, app: str, title: str, message: str = "",
           subtitle: str = "", category: str = "Social", date: str = "") -> None:
    post(api, "/notifications/ingest", {
        "uid": uid, "event": "added", "category": category, "app": app,
        "title": title, "subtitle": subtitle, "message": message,
        "date": date or time.strftime("%Y%m%dT%H%M%S"),
    })


def device(api: str, **kw) -> None:
    post(api, "/notifications/device", kw)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default=DEFAULT_API)
    ap.add_argument("--scenario", default="all",
                    choices=["all", "stack", "call", "burst", "away", "clear"])
    ap.add_argument("--pause", type=float, default=1.5)
    args = ap.parse_args()
    api, pause = args.api, args.pause

    if args.scenario == "clear":
        post(api, "/notifications/clear", {})
        print("cleared")
        return

    device(api, connected=True, name="iPhone", battery=62,
           model="iPhone15,2", firmware="18.1")
    print("phone connected")

    if args.scenario in ("all", "stack"):
        # Same app + same title -> one stack of three.
        for i, msg in enumerate(["hey", "you around?", "ok nvm"], start=1):
            notify(api, f"s{i}", app="com.apple.MobileSMS",
                   title="Christian", message=msg)
            time.sleep(pause)
        # Different sender, same app -> its own stack.
        notify(api, "s9", app="com.apple.MobileSMS", title="Mom",
               message="call me when you can")
        time.sleep(pause)
        # Brand-titled app: the title IS the app name, so the body shape is
        # what separates these two into different stacks.
        notify(api, "r1", app="com.ring", title="Ring",
               message="There is a person at your front door", category="Other")
        notify(api, "r2", app="com.ring", title="Ring",
               message="Battery is low", category="Other")
        print("stacking fixtures posted")
        time.sleep(pause)

    if args.scenario in ("all", "burst"):
        # Four at once: only three toasts should ever be visible.
        for i, (app, title) in enumerate([
            ("com.linkedin.LinkedIn", "LinkedIn"),
            ("com.burbn.instagram", "Instagram"),
            ("com.hammerandchisel.discord", "Discord"),
            ("com.digitalextremes", "Warframe"),
        ], start=1):
            notify(api, f"b{i}", app=app, title=title,
                   message="something happened", category="Other")
        print("burst of 4 posted")
        time.sleep(pause)

    if args.scenario in ("all", "call"):
        # Two-stage, exactly as the bridge does it for a ringing phone:
        # metadata first so it shows immediately, text a moment later.
        post(api, "/notifications/ingest", {
            "uid": "call1", "event": "added", "category": "IncomingCall"})
        time.sleep(0.4)
        notify(api, "call1", app="com.apple.mobilephone", title="Christian",
               message="mobile", category="IncomingCall")
        print("incoming call posted (metadata then attributes)")
        time.sleep(pause)

    if args.scenario in ("all", "away"):
        device(api, connected=False)
        print("phone away -> list withheld (held > 0, notifications empty)")
        time.sleep(pause * 2)
        device(api, connected=True, name="iPhone", battery=58)
        print("phone back -> list returns intact")

    with urllib.request.urlopen(f"{api}/notifications", timeout=4) as r:
        state = json.load(r)
    print(f"\nfinal: held={state['held']} shown={len(state['notifications'])} "
          f"linked={state['linked']} seq={state['seq']}")


if __name__ == "__main__":
    main()
