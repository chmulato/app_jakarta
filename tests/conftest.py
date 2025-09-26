from __future__ import annotations

import os
import time
from typing import Dict, Iterable, List
from urllib.parse import urljoin

import pytest
import requests

try:
    import psycopg2
    from psycopg2 import OperationalError
except Exception:  # pragma: no cover
    psycopg2 = None  # type: ignore
    OperationalError = Exception  # type: ignore[misc]

DEFAULT_BASE_URL = "http://localhost:9090/caracore-hub/"
DEFAULT_WAIT_SECONDS = 30


def _ensure_trailing_slash(value: str) -> str:
    return value if value.endswith("/") else value + "/"


@pytest.fixture(scope="session")
def base_url() -> str:
    return _ensure_trailing_slash(os.getenv("APP_TEST_BASE_URL", DEFAULT_BASE_URL))


@pytest.fixture(scope="session")
def api_base_url(base_url: str) -> str:
    return _ensure_trailing_slash(urljoin(base_url, "api/"))


@pytest.fixture(scope="session")
def admin_credentials() -> Dict[str, str]:
    return {
        "email": os.getenv("APP_TEST_ADMIN_EMAIL", "admin@meuapp.com"),
        "password": os.getenv("APP_TEST_ADMIN_PASSWORD", "Admin@123"),
    }


@pytest.fixture(scope="session")
def ensure_app_running(base_url: str):
    login_url = urljoin(base_url, "login")
    wait_seconds = int(os.getenv("APP_TEST_WAIT_SECONDS", str(DEFAULT_WAIT_SECONDS)))
    deadlines = time.time() + max(wait_seconds, 1)
    last_error: str | None = None
    while time.time() < deadlines:
        try:
            response = requests.get(login_url, timeout=5, allow_redirects=True)
            if response.status_code < 500:
                return {"login_url": login_url, "status_code": response.status_code}
            last_error = f"HTTP {response.status_code}"
        except requests.RequestException as exc:  # pragma: no cover - network errors
            last_error = str(exc)
        time.sleep(2)
    pytest.skip(f"Aplicação indisponível em {login_url}: {last_error}")


@pytest.fixture()
def api_session(ensure_app_running) -> requests.Session:
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": "app-jakarta-pytest/1.0",
            "Accept": "application/json,text/html;q=0.9,*/*;q=0.8",
            "Accept-Language": "pt-BR,pt;q=0.9,en;q=0.8",
        }
    )
    yield session
    session.close()


@pytest.fixture(scope="session")
def db_config() -> Dict[str, str]:
    return {
        "host": os.getenv("APP_TEST_DB_HOST", "localhost"),
        "port": int(os.getenv("APP_TEST_DB_PORT", "5432")),
        "dbname": os.getenv("APP_TEST_DB_NAME", "meu_app_db"),
        "user": os.getenv("APP_TEST_DB_USER", "meu_app_user"),
        "password": os.getenv("APP_TEST_DB_PASSWORD", "meu_app_password"),
    }


def _cleanup_pedidos(codigos: Iterable[str], db_config: Dict[str, str]) -> None:
    items = list(codigos)
    if not items:
        return
    if psycopg2 is None:  # pragma: no cover - fallback para ambientes sem psycopg2
        return
    try:
        conn = psycopg2.connect(connect_timeout=5, **db_config)
    except OperationalError:  # pragma: no cover - banco indisponível
        return
    try:
        with conn:
            with conn.cursor() as cur:
                for codigo in items:
                    cur.execute("DELETE FROM pedido WHERE codigo = %s", (codigo,))
    finally:
        conn.close()


@pytest.fixture()
def pedido_cleanup(db_config):
    codigos: List[str] = []
    yield codigos
    _cleanup_pedidos(codigos, db_config)
