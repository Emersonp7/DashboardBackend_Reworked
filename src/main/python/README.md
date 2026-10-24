# Phone notifications (Python)

Two processes that together put iPhone notifications on the dashboard:

| | What | Runs as | Interpreter |
| --- | --- | --- | --- |
| `ancs_bridge/` | Owns the Bluetooth link, decodes ANCS, POSTs to the API | **root** | `/usr/bin/python3` |
| `dashboard_notifications/` | HTTP API the dashboard polls | `perez` | `.venv` (3.11) |

This directory is **not part of the Gradle build**. `build.gradle` declares no
`sourceSets` for it, so nothing here can end up in the boot jar.

The Java backend on :8080 is not involved at all — notifications never touch it.

## Why two interpreters

`dbus-python` and `PyGObject` are the apt packages `python3-dbus` /
`python3-gi`, and are not realistically pip-installable on a Pi. The bridge
therefore runs on the **system** interpreter and is **stdlib-only**.

> **Rule: never add a pip dependency to `ancs_bridge/`.** If you ever need one
> venv for both, it must be created with `--system-site-packages`.

The API has no such constraint and gets a plain venv.

## Running

```bash
# API
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python -m dashboard_notifications          # 127.0.0.1:8001

# Bridge (needs root for the BLE advertisement + pairing agent)
sudo /usr/bin/python3 -m ancs_bridge --api http://127.0.0.1:8001/api
```

Systemd units live in `../../../deploy/`. **Only one process may hold the ANCS
advertisement and pairing agent**, so stop the legacy `dashboard-ancs.service`
before starting the new bridge.

## The URL shape

The router prefix is the full `/api/notifications`, which makes three things
line up with no rewriting:

- the bridge concatenates `--api` with `/notifications/...`, so pointing it at
  `…:8001/api` yields `/api/notifications/...` with **no bridge code change**;
- the frontend keeps its existing `/api/*` convention and single `BASE_URL`;
- Vite proxies `/api/notifications` straight through.

Do **not** swap this for `root_path="/api"` plus a `/notifications` prefix.
`root_path` only affects URL generation and OpenAPI, not Starlette routing, so
every request would 404.

`/docs` and `/openapi.json` sit at the service root and are deliberately *not*
proxied — reachable on :8001 directly, invisible from the kiosk.

## Endpoints

| Method | Path | Who calls it |
| --- | --- | --- |
| POST | `/api/notifications/ingest` | bridge |
| POST | `/api/notifications/device` | bridge |
| GET | `/api/notifications` | dashboard **and** bridge (for `boot_id`) |
| GET | `/api/notifications/health` | dashboard |
| DELETE | `/api/notifications/{uid}` | dashboard |
| POST | `/api/notifications/clear` | dashboard |

## Three behaviours that look like bugs and are not

**1. `boot_id` — restart recovery runs in both directions.**
If the *bridge* restarts, iOS replays its whole active set on re-subscribe. If
the *API* restarts, iOS has no reason to replay anything, so the store would sit
empty forever. `boot_id` changes on every process start; the bridge polls it
every 15s and re-posts its own mirror when it changes, flagged `pre_existing` so
nothing re-announces. Remove `boot_id` and the phone page stays blank after
every API restart until you happen to walk out of Bluetooth range and back.

A replay must be followed by a device re-report, or the list comes back while
`linked` is still false and stays withheld — invisible. That is why
`ApiSink.check_backend()` takes an `on_replay` callback.

**2. The list is withheld, not discarded, when the phone is away.**
`notifications: []` with `held: N` and the entries still in memory. While the
phone is out of range, things get read, replied to and dismissed on the phone
and we never hear about it — a stale snapshot is worse than showing nothing.
It returns intact the moment the link does. Note `total` is the *returned*
count (0 while withheld) while `held` is the real one.

**3. Liveness comes from the device heartbeat, never notification traffic.**
A phone with nothing to say is still a connected phone. Keying the link state
off the last ingest reported a perfectly healthy link as down after fifteen
quiet minutes. `DEVICE_STALE_AFTER` (420s) tolerates ~3 missed beats of the
bridge's 120s heartbeat.

Also non-obvious: eviction at `MAX_ACTIVE` drops the **least recently touched**
(insertion order is maintained by `move_to_end` on every ingest), while the
response sorts by `received_ts`, i.e. **first seen**. Two orderings, both
deliberate. And `received_ts` is preserved across a `modified` event, because
the bridge posts twice for every notification — once with metadata, once with
the text — and without that every notification would announce itself twice.

## Configuration

| Env var | Default | Notes |
| --- | --- | --- |
| `NOTIFICATIONS_HOST` | `127.0.0.1` | |
| `NOTIFICATIONS_PORT` | `8001` | |
| `NOTIFICATIONS_MAX_ACTIVE` | `60` | |
| `NOTIFICATIONS_DEVICE_STALE_AFTER` | `420` | Set to `5` to test the withholding path without waiting seven minutes |
| `NOTIFICATIONS_CORS_ORIGINS` | kiosk + vite ports | Only used for direct access; the proxy keeps it same-origin |

## Testing without a phone

```bash
API=http://127.0.0.1:8001/api/notifications
curl -s $API/health
curl -s -X POST $API/device -H 'Content-Type: application/json' \
     -d '{"connected":true,"name":"iPhone","battery":62}'
curl -s -X POST $API/ingest -H 'Content-Type: application/json' \
     -d '{"uid":"1","category":"Social","app":"com.apple.MobileSMS","title":"Christian","message":"hey"}'
curl -s $API | python3 -m json.tool

# Withholding
curl -s -X POST $API/device -H 'Content-Type: application/json' -d '{"connected":false}'
curl -s $API      # notifications: [], held: 1, battery still 62
```

`ancs_bridge.sink` has no BLE dependency, so the whole replay contract can be
exercised against a live API with no hardware — point an `ApiSink` at it,
restart the service, call `check_backend(on_replay=...)` and watch the mirror
come back. See `scripts/seed_notifications.py`.
