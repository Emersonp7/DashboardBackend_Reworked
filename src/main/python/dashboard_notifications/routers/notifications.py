"""Endpoints.

The prefix is the full ``/api/notifications`` rather than just
``/notifications`` so three things line up with no rewriting anywhere:

* the bridge, which concatenates ``--api`` with ``/notifications/...``, is
  pointed at ``http://127.0.0.1:8001/api`` and needs no code change;
* the frontend keeps its existing ``/api/*`` convention and its single
  ``BASE_URL`` logic;
* the Vite proxy can forward ``/api/notifications`` straight through.

Do not be tempted to use ``root_path="/api"`` with a ``/notifications`` prefix
instead — ``root_path`` only affects URL generation and OpenAPI, not Starlette's
routing, so every request would 404.
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException, Request

from ..models import Ack, DeviceAck, DeviceIn, DeviceOut, Health, IngestIn, Snapshot
from ..store import NotificationStore

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/notifications", tags=["Notifications"])


def _store(request: Request) -> NotificationStore:
    return request.app.state.store


# Declared before the `/{uid}` catch-all. DELETE is method-distinct so there is
# no real collision, but ordering removes the whole class of bug.
@router.get("/health", response_model=Health)
async def health() -> Health:
    return Health()


@router.post("/ingest", response_model=Ack)
async def ingest(incoming: IngestIn, request: Request) -> Ack:
    """Called by the ANCS bridge.

    A `removed` event clears a notification the user has already dealt with on
    the phone, which is what keeps this view honest.
    """
    return _store(request).ingest(incoming)


@router.post("/device", response_model=DeviceAck)
async def set_device(info: DeviceIn, request: Request) -> DeviceAck:
    """The bridge reports what it can see about the phone itself."""
    return DeviceAck(status="ok", device=_store(request).set_device(info))


@router.get("/device", response_model=DeviceOut)
async def get_device(request: Request) -> DeviceOut:
    return _store(request).device()


@router.post("/clear", response_model=Ack)
async def clear(request: Request) -> Ack:
    return _store(request).clear()


# Both spellings: the bridge polls the bare form, a browser may add the slash.
@router.get("", response_model=Snapshot)
@router.get("/", response_model=Snapshot, include_in_schema=False)
async def list_active(request: Request) -> Snapshot:
    """Newest first.

    `seq` changes whenever anything is added or removed, so a poller can skip
    re-rendering when nothing has happened — but note it does *not* change when
    the list becomes suppressed, so it is not sufficient on its own.
    """
    return _store(request).snapshot()


@router.delete("/{uid}", response_model=Ack)
async def dismiss(uid: str, request: Request) -> Ack:
    try:
        return _store(request).dismiss(uid)
    except KeyError:
        raise HTTPException(status_code=404, detail="unknown notification") from None
