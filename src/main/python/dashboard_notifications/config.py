"""Settings, read once from the environment at startup.

Plain ``os.environ`` rather than pydantic-settings: this service has five knobs
and runs on a Raspberry Pi, so every dependency avoided is one less wheel to
build.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field

DEFAULT_CORS_ORIGINS = (
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    "http://localhost:5173",
    "http://127.0.0.1:5173",
)


def _int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw)
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    host: str = "127.0.0.1"
    port: int = 8001

    #: Hard cap on retained notifications. The oldest-touched is evicted first.
    max_active: int = 60

    #: How long the phone may go unheard before the link counts as down.
    #:
    #: The bridge reports device state every 120s, so this tolerates roughly
    #: three missed beats. Configurable mainly so the staleness path can be
    #: tested in seconds instead of waiting seven minutes.
    device_stale_after: float = 420.0

    cors_origins: tuple[str, ...] = field(default=DEFAULT_CORS_ORIGINS)

    @classmethod
    def from_env(cls) -> "Settings":
        origins = os.environ.get("NOTIFICATIONS_CORS_ORIGINS", "")
        parsed = tuple(o.strip() for o in origins.split(",") if o.strip())
        return cls(
            host=os.environ.get("NOTIFICATIONS_HOST", "127.0.0.1"),
            port=_int("NOTIFICATIONS_PORT", 8001),
            max_active=_int("NOTIFICATIONS_MAX_ACTIVE", 60),
            device_stale_after=float(_int("NOTIFICATIONS_DEVICE_STALE_AFTER", 420)),
            cors_origins=parsed or DEFAULT_CORS_ORIGINS,
        )
