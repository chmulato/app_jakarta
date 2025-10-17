from __future__ import annotations

import http
import random
import string
from typing import Any, Dict
from urllib.parse import urljoin

import pytest


def _random_code(prefix: str = "TEST-PED-") -> str:
    suffix = "".join(random.choices(string.ascii_uppercase + string.digits, k=6))
    return f"{prefix}{suffix}"


@pytest.mark.usefixtures("ensure_app_running")
def test_criar_e_retornar_flujo_pedido(api_base_url: str, api_session, pedido_cleanup):
    codigo = _random_code()
    pedido_cleanup.append(codigo)

    create_payload: Dict[str, Any] = {
        "codigo": codigo,
        "destinatarioNome": "Cliente Teste",
        "destinatarioDocumento": "11122233344",
        "destinatarioTelefone": "11988887777",
        "canal": "MANUAL",
        "volumes": [
            {
                "etiqueta": f"{codigo}-VOL-01",
                "peso": 2.5,
                "dimensoes": "30x20x15",
            }
        ],
        "actor": "pytest",
    }

    create_url = urljoin(api_base_url, "pedidos")
    response = api_session.post(create_url, json=create_payload, timeout=15)
    assert response.status_code == http.HTTPStatus.CREATED
    created = response.json()
    pedido_id = created["id"]
    assert created["status"] == "RECEBIDO"
    assert created["codigo"] == codigo

    ready_url = urljoin(api_base_url, f"pedidos/{pedido_id}/ready")
    response_ready = api_session.post(ready_url, timeout=10)
    assert response_ready.status_code == http.HTTPStatus.OK
    assert response_ready.json()["status"] == "PRONTO"

    pickup_url = urljoin(api_base_url, f"pedidos/{pedido_id}/pickup")
    response_pickup = api_session.post(pickup_url, timeout=10)
    assert response_pickup.status_code == http.HTTPStatus.OK
    assert response_pickup.json()["status"] == "RETIRADO"

    list_url = urljoin(api_base_url, f"pedidos?status=RETIRADO&destinatario={create_payload['destinatarioNome']}")
    response_list = api_session.get(list_url, timeout=10)
    assert response_list.status_code == http.HTTPStatus.OK
    itens = response_list.json()
    assert any(item["codigo"] == codigo and item["status"] == "RETIRADO" for item in itens)
