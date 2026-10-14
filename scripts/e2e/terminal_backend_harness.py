#!/usr/bin/env python3
"""Processo E2E que usa as classes reais do Terminal Python contra o Spring real."""
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import requests

TERMINAL_ROOT = Path(os.environ["TERMINAL_PYTHON_ROOT"]).resolve()
sys.path.insert(0, str(TERMINAL_ROOT))

from database.ActivePaymentStore import ActivePaymentStore
from database.DatabaseProdutos import DatabaseProdutos
from model.Terminal import Terminal
from service.BackendClient import BackendClient, BackendHttpError
from service.PurchaseApi import PurchaseApi
from service.SyncService import SyncService
from service.TelemetryService import TelemetryService
from service.TerminalAuth import TerminalCredentialStore, migrate_legacy_credential
from service.TerminalSocket import TerminalSocket

BASE = os.environ["API_URL"].rstrip("/")
WS = os.environ["WS_URL"].rstrip("/")
POINT = os.environ["POINT_STUB_URL"].rstrip("/")
TERMINAL_ID = "30000000-0000-0000-0000-000000000001"
PRODUCT_ID = "40000000-0000-0000-0000-000000000001"
RESULTS = {}


def check(name, condition, detail=""):
    if not condition:
        raise AssertionError(f"{name}: {detail or 'condição não atendida'}")
    RESULTS[name] = "OK" if not detail else f"OK — {detail}"
    print(f"[E2E] {name}: {RESULTS[name]}", flush=True)


def sql(query):
    command = [
        "docker", "compose", "-f", os.environ["E2E_COMPOSE_FILE"],
        "exec", "-T", "mysql-e2e", "mysql", "-N", "-B",
        "-uapp247_e2e", f"-p{os.environ['E2E_DB_PASSWORD']}", "app247_e2e",
    ]
    completed = subprocess.run(command, input=query, text=True, capture_output=True, check=True)
    return completed.stdout.strip()


def point_state(state, timeout=0):
    response = requests.post(f"{POINT}/__control/state", json={"state": state, "timeoutSeconds": timeout}, timeout=5)
    response.raise_for_status()


class Collector:
    def __init__(self, value): self.value = value
    def collect(self): return self.value


def payment(api, store, state, *, timeout=0):
    requests.post(f"{POINT}/__control/reset", timeout=5).raise_for_status()
    point_state(state, timeout)
    local_id = f"local-{time.time_ns()}"
    store.save({"terminal_id": TERMINAL_ID, "generation": local_id,
                "local_attempt_id": local_id, "status": "STARTING"})
    result = api.start_point(
        {"terminalId": TERMINAL_ID, "items": [{"productId": PRODUCT_ID, "quantity": 1,
          "expectedUnitPrice": 13.45, "codigoBarras": "7890000000001"}]},
        lambda cart_id: store.checkpoint_cart(local_id, cart_id),
    )
    snapshot = store.load()
    snapshot.update({"cart_id": result["cartId"], "order_id": result["orderId"],
                     "payment_attempt_id": result.get("paymentAttemptId"),
                     "status": result.get("paymentStatus") or result.get("status")})
    store.save(snapshot)
    return result


def main():
    runtime = Path(os.environ.get("E2E_RUNTIME_DIR") or tempfile.mkdtemp(prefix="app247-e2e-terminal-"))
    runtime.mkdir(parents=True, exist_ok=True)
    credential = TerminalCredentialStore(runtime / "device-credential")
    terminal_path = runtime / "terminal.json"
    db_path = runtime / "terminal.db"
    cursor_path = runtime / "last_sync.txt"
    terminal = Terminal(TERMINAL_ID, TERMINAL_ID, "SERIAL-E2E-001", "Terminal E2E",
                        "TERM-E2E-001", "OFFLINE", True, True,
                        "20000000-0000-0000-0000-000000000001", "Condominio E2E")
    terminal.save(terminal_path)

    migrated = migrate_legacy_credential(TERMINAL_ID, BASE, requests.Session(), credential)
    check("device credential", migrated["terminalId"] == TERMINAL_ID)
    check("credential 0600", oct((runtime / "device-credential").stat().st_mode & 0o777) == "0o600")
    client = BackendClient(BASE, credential_store=credential)
    check("auth válida", client.request("GET", f"/terminal/{TERMINAL_ID}/bootstrap", terminal_id=TERMINAL_ID).status_code == 200)
    missing = requests.get(f"{BASE}/terminal/{TERMINAL_ID}/bootstrap", timeout=5)
    invalid = requests.get(f"{BASE}/terminal/{TERMINAL_ID}/bootstrap", headers={"X-Terminal-Token":"invalid"}, timeout=5)
    check("auth ausente", missing.status_code == 401 and missing.json().get("code") == "AUTH_REQUIRED")
    check("auth inválida", invalid.status_code == 401 and invalid.json().get("code") == "TERMINAL_AUTH_INVALID")

    sync = SyncService(api_url=BASE, db_path=db_path, last_sync_path=cursor_path,
                       terminal_path=terminal_path, credential_store=credential)
    check("FULL sync", sync.sincronizar_produtos("E2E_FULL"))
    db = DatabaseProdutos(db_path); counts = db.contar_produtos(); first_cursor = db.obter_ultimo_sync(); db.close()
    check("catálogo SQLite", counts["ativos"] == 1 and first_cursor and first_cursor.endswith("Z"))
    empty_db_path = runtime / "terminal-empty-full.db"
    empty_cursor_path = runtime / "empty-full-cursor.txt"
    empty_sync = SyncService(api_url=BASE, db_path=empty_db_path, last_sync_path=empty_cursor_path,
                             terminal_path=terminal_path, credential_store=credential)
    check("FULL preparatório vazio", empty_sync.sincronizar_produtos("E2E_EMPTY_PREPARE"))
    with sqlite3.connect(empty_db_path) as connection:
        connection.execute("UPDATE catalog_sync_state SET initialized=0,last_sync_at=NULL WHERE singleton_id=1")
    sql("UPDATE estoque_condominio SET ativo=FALSE,updated_at=UTC_TIMESTAMP(6) WHERE id='50000000-0000-0000-0000-000000000001';")
    check("FULL vazio", empty_sync.sincronizar_produtos("E2E_EMPTY_FULL"))
    empty_db = DatabaseProdutos(empty_db_path); empty_counts = empty_db.contar_produtos(); empty_db.close()
    check("FULL vazio remove obsoleto", empty_counts["ativos"] == 0)
    sql("UPDATE estoque_condominio SET ativo=TRUE,updated_at=UTC_TIMESTAMP(6) WHERE id='50000000-0000-0000-0000-000000000001';")
    sql("UPDATE produto SET nome='Produto E2E Atualizado', preco_venda=13.450000, updated_at=UTC_TIMESTAMP(6) WHERE id='40000000-0000-0000-0000-000000000001';")
    check("incremental sync", sync.sincronizar_produtos("E2E_INCREMENTAL"))
    db = DatabaseProdutos(db_path); row = db.buscar_por_codigo("7890000000001"); second_cursor = db.obter_ultimo_sync(); db.close()
    check("cursor incremental", row and row[2] == "Produto E2E Atualizado" and second_cursor != first_cursor)
    preserved = second_cursor
    sql("UPDATE terminal SET ativo=FALSE WHERE id='30000000-0000-0000-0000-000000000001';")
    check("403 terminal disabled", requests.get(f"{BASE}/produtos/sync", params={"uuidTerminal":TERMINAL_ID}, headers={"X-Terminal-Token":credential.load()}, timeout=5).status_code == 403)
    sql("UPDATE terminal SET ativo=TRUE WHERE id='30000000-0000-0000-0000-000000000001';")
    db = DatabaseProdutos(db_path); check("cursor preservado em erro", db.obter_ultimo_sync() == preserved); db.close()

    heartbeat = TerminalSocket(ws_url=WS, terminal_path=terminal_path, interval_seconds=30,
                               retry_seconds=1, credential_store=credential)
    heartbeat.start()
    deadline=time.time()+8
    while heartbeat.connection_state != "CONNECTED" and time.time() < deadline: time.sleep(.1)
    check("heartbeat WebSocket", heartbeat.connection_state == "CONNECTED")
    heartbeat.stop()
    check("heartbeat persistido", sql("SELECT last_ping IS NOT NULL FROM terminal WHERE id='30000000-0000-0000-0000-000000000001';") == "1")

    telemetry = TelemetryService(
        sync, None, lambda: "DISCONNECTED", None, api_url=BASE, terminal_path=terminal_path,
        credential_store=credential,
        system_collector=Collector({"uptimeSeconds":1,"cpuUsagePercent":1.0,"memoryUsedBytes":1,"memoryTotalBytes":2,"memoryUsagePercent":50.0,"diskUsedBytes":1,"diskTotalBytes":2,"diskUsagePercent":50.0}),
        network_collector=Collector({"connected":True,"interfaceName":"lo","localIp":"127.0.0.1","wifiSignalPercent":100,"wifiSignalQuality":"EXCELLENT","backendReachable":True,"backendLatencyMs":1}),
        application_collector=Collector({"version":"e2e","uptimeSeconds":1,"websocketStatus":"DISCONNECTED","purchaseActive":False,"paymentInProgress":False}),
        display_collector=Collector({"width":1080,"height":1920,"orientation":"VERTICAL"}),
    )
    check("telemetria", telemetry.collect_and_send())

    # Volta o preço visual esperado ao valor persistido antes de exercitar finanças.
    api = PurchaseApi(BASE, terminal_id=TERMINAL_ID, credential_store=credential)
    store = ActivePaymentStore(db_path)
    pending = payment(api, store, "PENDING")
    check("carrinho + PaymentAttempt PENDING", pending.get("orderId") and pending.get("paymentAttemptId"))
    check("checkpoint SQLite", store.load()["order_id"] == pending["orderId"])
    recovered = api.get_active_payment(TERMINAL_ID)
    check("restart Terminal/recovery PENDING", recovered and recovered.get("orderId") == pending["orderId"])
    point_state("APPROVED")
    approved = api.get_order(pending["orderId"], TERMINAL_ID)
    check("APPROVED/reconciliation", approved.get("status") == "APPROVED" and approved.get("attemptStatus") == "PROCESSED")
    receipt = api.send_receipt(pending["orderId"], TERMINAL_ID, "EMAIL", "e2e@example.invalid")
    check("comprovante Terminal -> Spring", receipt.get("status") == "ENVIADO")
    store.clear()

    unknown = payment(api, store, "PENDING", timeout=3)
    check("timeout -> UNKNOWN", (unknown.get("attemptStatus") or "").upper() == "UNKNOWN")
    before = requests.get(f"{POINT}/__control", timeout=5).json()["createCalls"]
    repeated = api.resume_point(unknown["cartId"], TERMINAL_ID)
    after = requests.get(f"{POINT}/__control", timeout=5).json()["createCalls"]
    check("retry sem duplicidade", before == after == 1, f"operações remotas={after}")
    point_state("APPROVED"); time.sleep(5.2)
    reconciled = api.get_order(unknown["orderId"], TERMINAL_ID)
    check("UNKNOWN -> APPROVED", (reconciled.get("attemptStatus") or "").upper() == "PROCESSED")
    check("idempotência remota", requests.get(f"{POINT}/__control", timeout=5).json()["createCalls"] == 1, "operações remotas=1")
    store.clear()

    rejected = payment(api, store, "REJECTED")
    check("REJECTED", rejected.get("status") == "REJECTED" and (rejected.get("attemptStatus") or "").upper() == "FAILED")
    point_state("PENDING")
    retry = api.resume_point(rejected["cartId"], TERMINAL_ID)
    attempts = int(sql(f"SELECT COUNT(*) FROM payment_attempt WHERE order_id='{rejected['orderId']}';"))
    remote_calls = requests.get(f"{POINT}/__control", timeout=5).json()["createCalls"]
    check("retry financeiro", retry.get("paymentAttemptId") != rejected.get("paymentAttemptId")
          and attempts == 2 and remote_calls == 2,
          f"attempts={attempts}, operações remotas={remote_calls}")
    cancelled = api.cancel_point(retry["orderId"], TERMINAL_ID)
    check("cancelamento", (cancelled.get("attemptStatus") or "").upper() == "CANCELED")
    store.clear()

    with sqlite3.connect(db_path) as connection:
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
    check("SQLite integrity_check", integrity == "ok")
    print(json.dumps({"runtime": str(runtime), "results": RESULTS}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
