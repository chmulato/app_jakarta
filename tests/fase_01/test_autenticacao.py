from __future__ import annotations

import http
from urllib.parse import urljoin

import pytest


@pytest.mark.usefixtures("ensure_app_running")
def test_login_sucesso(base_url: str, admin_credentials: dict[str, str], api_session):
    login_url = urljoin(base_url, "login")
    payload = {
        "email": admin_credentials["email"],
        "senha": admin_credentials["password"],
    }
    response = api_session.post(login_url, data=payload, allow_redirects=False, timeout=10)
    assert response.status_code in {
        http.HTTPStatus.MOVED_PERMANENTLY,
        http.HTTPStatus.FOUND,
        http.HTTPStatus.SEE_OTHER,
        http.HTTPStatus.TEMPORARY_REDIRECT,
        http.HTTPStatus.PERMANENT_REDIRECT,
    }
    location = response.headers.get("Location", "")
    assert "login" not in location


@pytest.mark.usefixtures("ensure_app_running")
def test_login_credenciais_invalidas(base_url: str, api_session):
    login_url = urljoin(base_url, "login")
    payload = {"email": "naoexiste@example.com", "senha": "senha@123"}
    response = api_session.post(login_url, data=payload, allow_redirects=False, timeout=10)
    # Fluxo atual retorna 200 com mensagem de erro; toleramos 302 -> /login também
    assert response.status_code in {http.HTTPStatus.OK, http.HTTPStatus.FOUND}
