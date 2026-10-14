#!/usr/bin/env python3
"""E2E real: App -> QR -> Terminal -> compra -> loyalty -> refund."""
import os
import subprocess
import sys
from pathlib import Path

import requests


TERMINAL_ROOT = Path(os.environ["TERMINAL_PYTHON_ROOT"]).resolve()
sys.path.insert(0, str(TERMINAL_ROOT))

from service.PurchaseApi import PurchaseApi
from service.ScannerRouter import ScanType, ScannerRouter
from service.TerminalAuth import TerminalCredentialStore


BASE = os.environ["API_URL"].rstrip("/")
POINT = os.environ["POINT_STUB_URL"].rstrip("/")
TERMINAL_ID = "30000000-0000-0000-0000-000000000001"
EMPRESA_ID = "10000000-0000-0000-0000-000000000001"
CONDOMINIO_ID = "20000000-0000-0000-0000-000000000001"
PRODUCT_ID = "40000000-0000-0000-0000-000000000001"
CPF = "52998224725"
PASSWORD = "QrE2e-247!"


def check(name, condition, detail=""):
    if not condition:
        raise AssertionError(f"{name}: {detail or 'condição não atendida'}")
    suffix = f" — {detail}" if detail else ""
    print(f"[E2E-QR] {name}: OK{suffix}", flush=True)


def sql(query):
    command = [
        "docker", "compose", "-f", os.environ["E2E_COMPOSE_FILE"],
        "exec", "-T", "mysql-e2e", "mysql", "-N", "-B",
        "-uapp247_e2e", f"-p{os.environ['E2E_DB_PASSWORD']}", "app247_e2e",
    ]
    completed = subprocess.run(command, input=query, text=True, capture_output=True, check=True)
    return completed.stdout.strip()


def request(method, path, **kwargs):
    response = requests.request(method, f"{BASE}{path}", timeout=10, **kwargs)
    if not response.ok:
        raise AssertionError(f"{method} {path}: HTTP {response.status_code} {response.text}")
    return response.json() if response.content else None


def main():
    registered = request("POST", "/auth/register", json={
        "nome": "Cliente QR", "sobrenome": "E2E", "cpf": CPF,
        "email": "cliente-qr-e2e@example.invalid", "telefone": "71999999999",
        "senha": PASSWORD, "termsAccepted": True, "privacyAccepted": True,
    })
    user_id = registered["user"]["id"]
    sql(
        "UPDATE users SET empresa_id='" + EMPRESA_ID + "', condominio_id='" + CONDOMINIO_ID
        + "', email_verificado=TRUE, cpf_verified_at=UTC_TIMESTAMP(6) WHERE id='" + user_id + "';"
        "INSERT INTO app_cliente_config (empresa_id,catalogo,promocoes,fidelidade,pontos,cashback,"
        "recompensas,cupons,qr_identificacao,preferencias,suporte,loyalty_mode,points_per_currency,"
        "cashback_percent,created_at,updated_at) VALUES ('" + EMPRESA_ID
        + "',TRUE,FALSE,TRUE,TRUE,FALSE,TRUE,TRUE,TRUE,FALSE,FALSE,'POINTS',1.000000,NULL,"
        "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE fidelidade=TRUE,pontos=TRUE,"
        "cashback=FALSE,recompensas=TRUE,cupons=TRUE,qr_identificacao=TRUE,loyalty_mode='POINTS',"
        "points_per_currency=1.000000,cashback_percent=NULL,updated_at=UTC_TIMESTAMP(6);"
    )

    login = request("POST", "/auth/login", json={"cpf": CPF, "senha": PASSWORD})
    bearer = {"Authorization": f"Bearer {login['token']}"}
    issued = request("POST", "/app-cliente/identificacao-tokens", headers=bearer)
    qr_payload = issued["qrPayload"]
    check("User -> token", qr_payload.startswith("app247://customer-link/"))
    check("QR sem PII", CPF not in qr_payload and user_id not in qr_payload)

    scanned = ScannerRouter().parse(qr_payload + "\n")
    check("scanner CUSTOMER_LINK_TOKEN", scanned.type == ScanType.CUSTOMER_LINK_TOKEN)

    credential = TerminalCredentialStore(Path(os.environ["DEVICE_CREDENTIAL_PATH"]))
    api = PurchaseApi(BASE, terminal_id=TERMINAL_ID, credential_store=credential)
    cart_payload = {
        "terminalId": TERMINAL_ID,
        "items": [{"productId": PRODUCT_ID, "quantity": 1,
                   "expectedUnitPrice": 13.45, "codigoBarras": "7890000000001"}],
    }
    linked = api.link_customer(scanned.value, cart_payload)
    check("Terminal -> Spring", linked.get("linked") is True)
    check("cart -> User", sql(
        f"SELECT user_id FROM carrinho WHERE id='{linked['cartId']}';"
    ) == user_id)

    requests.post(f"{POINT}/__control/reset", timeout=5).raise_for_status()
    requests.post(f"{POINT}/__control/state", json={"state": "APPROVED", "timeoutSeconds": 0},
                  timeout=5).raise_for_status()
    stock_before = sql(
        f"SELECT quantidade FROM estoque_condominio WHERE produto_id='{PRODUCT_ID}' "
        f"AND condominio_id='{CONDOMINIO_ID}';"
    )
    paid = api.start_point(cart_payload, existing_cart_id=linked["cartId"])
    order_id = paid["orderId"]
    check("Order -> User", sql(
        f"SELECT user_id FROM orders WHERE id='{order_id}';"
    ) == user_id)
    check("APPROVED", paid.get("status") == "APPROVED")

    history = request("GET", "/app-cliente/compras", headers=bearer)
    history_ids = {item["id"] for item in history["content"]}
    check("APPROVED -> histórico", order_id in history_ids)
    detail = request("GET", f"/app-cliente/compras/{order_id}", headers=bearer)
    check("detalhe com ownership", detail["id"] == order_id)
    movements = int(sql(
        f"SELECT COUNT(*) FROM loyalty_movement WHERE order_id='{order_id}' "
        f"AND idempotency_key='purchase:{order_id}';"
    ))
    check("loyalty exatamente uma vez", movements == 1)

    sql(f"UPDATE users SET role='ADMIN' WHERE id='{user_id}';")
    admin_login = request("POST", "/auth/login", json={"cpf": CPF, "senha": PASSWORD})
    admin_bearer = {"Authorization": f"Bearer {admin_login['token']}"}
    refunded = request("POST", f"/admin/orders/{order_id}/refund", headers=admin_bearer)
    check("refund confirmado", refunded.get("confirmed") is True and refunded.get("status") == "refunded")
    check("Order REFUNDED", sql(f"SELECT status FROM orders WHERE id='{order_id}';") == "REFUNDED")
    check("PaymentAttempt REFUNDED", sql(
        f"SELECT status FROM payment_attempt WHERE order_id='{order_id}' ORDER BY created_at DESC LIMIT 1;"
    ) == "REFUNDED")
    check("estoque recomposto", sql(
        f"SELECT quantidade FROM estoque_condominio WHERE produto_id='{PRODUCT_ID}' "
        f"AND condominio_id='{CONDOMINIO_ID}';"
    ) == stock_before)
    reversal_count = int(sql(
        f"SELECT COUNT(*) FROM loyalty_movement WHERE order_id='{order_id}' "
        f"AND idempotency_key='refund:{order_id}';"
    ))
    check("estorno loyalty exatamente uma vez", reversal_count == 1)
    loyalty_balance = sql(
        f"SELECT CONCAT(points_balance,':',cashback_balance) FROM loyalty_account "
        f"WHERE user_id='{user_id}' AND empresa_id='{EMPRESA_ID}';"
    )
    check("saldo loyalty compensado", loyalty_balance in {"0:0.000000", "0:0"}, loyalty_balance)
    refunded_detail = request("GET", f"/app-cliente/compras/{order_id}", headers=bearer)
    check("reembolso no histórico", refunded_detail.get("status") == "REFUNDED")

    repeated = request("POST", f"/admin/orders/{order_id}/refund", headers=admin_bearer)
    check("refund idempotente", repeated.get("confirmed") is True)
    check("estorno loyalty não duplica", int(sql(
        f"SELECT COUNT(*) FROM loyalty_movement WHERE idempotency_key='refund:{order_id}';"
    )) == 1)
    print("[E2E-QR] APP -> QR -> TERMINAL -> ORDER -> HISTÓRICO -> LOYALTY -> REFUND: aprovado")


if __name__ == "__main__":
    main()
