"""FastAPI application factory."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .config import Settings
from .routers import notifications
from .store import NotificationStore

log = logging.getLogger(__name__)


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings.from_env()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.settings = settings
        app.state.store = NotificationStore(
            max_active=settings.max_active,
            device_stale_after=settings.device_stale_after,
        )
        # Logged on purpose: this is the value the bridge watches to decide
        # whether to replay, so it is the first thing to grep for when the
        # phone page comes back empty after a restart.
        log.info(
            "notifications store ready (boot_id=%s, max_active=%d, stale_after=%.0fs)",
            app.state.store.boot_id,
            settings.max_active,
            settings.device_stale_after,
        )
        yield

    app = FastAPI(
        title="Dashboard Notifications",
        description="iPhone notifications, fed by the ANCS bridge over BLE.",
        version="1.0.0",
        lifespan=lifespan,
    )

    # Never exercised in production: the frontend reaches this through Vite's
    # proxy, so it stays same-origin. This covers direct access during
    # development (VITE_API_BASE_URL set, or a curl from a browser console).
    #
    # DELETE matters here and is the reason dismissal could not be routed
    # through the Java backend: its CorsConfig allows only GET and POST.
    app.add_middleware(
        CORSMiddleware,
        allow_origins=list(settings.cors_origins),
        allow_methods=["GET", "POST", "DELETE"],
        allow_headers=["Content-Type"],
        allow_credentials=False,
    )

    app.include_router(notifications.router)
    return app


app = create_app()
