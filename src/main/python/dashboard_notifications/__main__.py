"""Dev convenience: ``python -m dashboard_notifications``.

Production runs uvicorn directly from the systemd unit so the worker count and
logging flags live in one place; this exists so the service can be started by
hand without remembering the uvicorn incantation.
"""

from __future__ import annotations

import logging

import uvicorn

from .config import Settings


def main() -> None:
    logging.basicConfig(
        level=logging.INFO, format="%(asctime)s %(levelname)-7s %(name)s: %(message)s"
    )
    settings = Settings.from_env()
    uvicorn.run(
        "dashboard_notifications.app:app",
        host=settings.host,
        port=settings.port,
        workers=1,
        access_log=False,
    )


if __name__ == "__main__":
    main()
