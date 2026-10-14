#!/usr/bin/env python3
import json
import os
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


LOCK = threading.Lock()
STATE = "PENDING"
TIMEOUT_SECONDS = 0.0
CREATE_CALLS = 0
VERSION = 1
ORDERS = {}
IDEMPOTENCY = {}
REFUND_IDEMPOTENCY = {}

REMOTE = {
    "PENDING": ("created", "created"),
    "PROCESSING": ("at_terminal", "at_terminal"),
    "ACTION_REQUIRED": ("action_required", "action_required"),
    "APPROVED": ("processed", "accredited"),
    "REJECTED": ("failed", "rejected"),
    "CANCELLED": ("canceled", "canceled"),
    "REFUNDED": ("refunded", "refunded"),
    "UNKNOWN": ("unknown", "unknown"),
}


def payload(order_id, external_reference, state=None):
    selected = state or STATE
    status, detail = REMOTE[selected]
    return {
        "id": order_id,
        "type": "point",
        "user_id": "e2e-mp-user",
        "external_reference": external_reference,
        "status": status,
        "status_detail": detail,
        "version": VERSION,
        "config": {"point": {"terminal_id": "POINT-E2E-001", "print_on_terminal": "no_ticket"}},
        "transactions": {"payments": [{"id": f"pay-{order_id}", "amount": "12.34", "status": status,
            "status_detail": detail,
            "payment_method": {"id": "visa", "type": "credit_card", "installments": 1}}]},
    }


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[POINT-STUB] " + (fmt % args), flush=True)

    def body(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size = int(self.rfile.readline().strip().split(b";", 1)[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            raw = b"".join(chunks)
        else:
            raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        return json.loads(raw or b"{}")

    def send_json(self, status, value):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        global STATE, CREATE_CALLS
        if self.path == "/health":
            return self.send_json(200, {"status": "UP"})
        if self.path == "/__control":
            with LOCK:
                return self.send_json(200, {"state": STATE, "createCalls": CREATE_CALLS, "orders": len(ORDERS)})
        if self.path.startswith("/v1/orders/"):
            order_id = self.path.split("/")[3]
            with LOCK:
                order = ORDERS.get(order_id)
                if not order:
                    return self.send_json(404, {"error": "not_found"})
                return self.send_json(200, payload(order_id, order["external_reference"]))
        return self.send_json(404, {"error": "not_found"})

    def do_POST(self):
        global STATE, TIMEOUT_SECONDS, CREATE_CALLS, VERSION
        if self.path == "/__control/reset":
            with LOCK:
                STATE, TIMEOUT_SECONDS, CREATE_CALLS, VERSION = "PENDING", 0.0, 0, 1
                ORDERS.clear(); IDEMPOTENCY.clear(); REFUND_IDEMPOTENCY.clear()
            return self.send_json(200, {"ok": True})
        if self.path == "/__control/state":
            request = self.body()
            selected = str(request.get("state", "")).upper()
            if selected not in REMOTE:
                return self.send_json(400, {"error": "invalid_state"})
            with LOCK:
                if selected != STATE:
                    VERSION += 1
                STATE = selected
                TIMEOUT_SECONDS = float(request.get("timeoutSeconds", 0) or 0)
            return self.send_json(200, {"state": STATE, "timeoutSeconds": TIMEOUT_SECONDS})
        if self.path == "/v1/orders":
            request = self.body()
            print("[POINT-STUB] create fields=" + ",".join(sorted(request.keys())), flush=True)
            key = self.headers.get("X-Idempotency-Key")
            external = request.get("external_reference")
            with LOCK:
                if key in IDEMPOTENCY:
                    order_id = IDEMPOTENCY[key]
                else:
                    CREATE_CALLS += 1
                    order_id = "stub-" + uuid.uuid4().hex
                    IDEMPOTENCY[key] = order_id
                    ORDERS[order_id] = {"external_reference": external}
                delay = TIMEOUT_SECONDS
            if delay:
                time.sleep(delay)
            return self.send_json(201, payload(order_id, external))
        if self.path.startswith("/v1/orders/") and self.path.endswith("/cancel"):
            order_id = self.path.split("/")[3]
            with LOCK:
                order = ORDERS.get(order_id)
                if not order:
                    return self.send_json(404, {"error": "not_found"})
                VERSION += 1
                STATE = "CANCELLED"
            return self.send_json(200, payload(order_id, order["external_reference"], "CANCELLED"))
        if self.path.startswith("/v1/orders/") and self.path.endswith("/refund"):
            order_id = self.path.split("/")[3]
            key = self.headers.get("X-Idempotency-Key")
            with LOCK:
                order = ORDERS.get(order_id)
                if not order:
                    return self.send_json(404, {"error": "not_found"})
                if not key:
                    return self.send_json(400, {"error": "missing_idempotency_key"})
                existing = REFUND_IDEMPOTENCY.get(key)
                if existing and existing != order_id:
                    return self.send_json(409, {"error": "idempotency_conflict"})
                REFUND_IDEMPOTENCY[key] = order_id
                VERSION += 1
                STATE = "REFUNDED"
                response = payload(order_id, order["external_reference"], "REFUNDED")
                response["transactions"] = {"refunds": [{
                    "id": f"refund-{order_id}", "amount": "13.45", "status": "processed"
                }]}
            return self.send_json(201, response)
        return self.send_json(404, {"error": "not_found"})


ThreadingHTTPServer(("0.0.0.0", int(os.getenv("POINT_STUB_PORT", "9091"))), Handler).serve_forever()
