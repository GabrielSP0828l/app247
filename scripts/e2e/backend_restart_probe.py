#!/usr/bin/env python3
import json
import os
import sys
import time
from pathlib import Path

import requests

terminal_root = Path(os.environ["TERMINAL_PYTHON_ROOT"]).resolve()
sys.path.insert(0, str(terminal_root))

from database.ActivePaymentStore import ActivePaymentStore
from service.PurchaseApi import PurchaseApi
from service.TerminalAuth import TerminalCredentialStore

TERMINAL_ID = "30000000-0000-0000-0000-000000000001"
PRODUCT_ID = "40000000-0000-0000-0000-000000000001"
runtime = Path(os.environ["E2E_RUNTIME_DIR"])
state_path = runtime / "backend-restart-probe.json"
store = ActivePaymentStore(runtime / "terminal.db")
credential = TerminalCredentialStore(runtime / "device-credential")
api = PurchaseApi(os.environ["API_URL"], terminal_id=TERMINAL_ID, credential_store=credential)

if sys.argv[1] == "prepare":
    point = os.environ["POINT_STUB_URL"].rstrip("/")
    requests.post(f"{point}/__control/reset", timeout=5).raise_for_status()
    requests.post(f"{point}/__control/state", json={"state":"PENDING"}, timeout=5).raise_for_status()
    local_id = f"restart-{time.time_ns()}"
    store.save({"terminal_id":TERMINAL_ID,"generation":local_id,
                "local_attempt_id":local_id,"status":"STARTING"})
    result = api.start_point(
        {"terminalId":TERMINAL_ID,"items":[{"productId":PRODUCT_ID,"quantity":1,
          "expectedUnitPrice":13.45,"codigoBarras":"7890000000001"}]},
        lambda cart_id: store.checkpoint_cart(local_id, cart_id),
    )
    snapshot=store.load(); snapshot.update({"cart_id":result["cartId"],"order_id":result["orderId"],
        "payment_attempt_id":result.get("paymentAttemptId"),"status":result.get("status")}); store.save(snapshot)
    state_path.write_text(json.dumps(result), encoding="utf-8")
    print(f"[E2E] backend restart preparado orderId={result['orderId']} attemptId={result.get('paymentAttemptId')}")
elif sys.argv[1] == "recover":
    expected=json.loads(state_path.read_text(encoding="utf-8"))
    recovered=api.get_active_payment(TERMINAL_ID)
    checkpoint=store.load(TERMINAL_ID)
    assert recovered and recovered.get("orderId")==expected["orderId"]
    assert recovered.get("paymentAttemptId")==expected.get("paymentAttemptId")
    assert checkpoint and checkpoint.get("order_id")==expected["orderId"]
    print("[E2E] restart backend/recovery PENDING: OK")
else:
    raise SystemExit("uso: backend_restart_probe.py prepare|recover")
