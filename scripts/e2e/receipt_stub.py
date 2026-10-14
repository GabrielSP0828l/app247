#!/usr/bin/env python3
import json, os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args): print("[RECEIPT-STUB] " + fmt % args, flush=True)
    def send_json(self, value):
        data=json.dumps(value).encode(); self.send_response(200); self.send_header("Content-Type","application/json"); self.send_header("Content-Length",str(len(data))); self.end_headers(); self.wfile.write(data)
    def do_GET(self): self.send_json({"status":"UP"}) if self.path=="/health" else self.send_error(404)
    def do_POST(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks=[]
            while True:
                size=int(self.rfile.readline().strip().split(b";",1)[0],16)
                if size==0: self.rfile.readline(); break
                chunks.append(self.rfile.read(size)); self.rfile.read(2)
            raw=b"".join(chunks)
        else:
            raw=self.rfile.read(int(self.headers.get("Content-Length","0")))
        body=json.loads(raw or b"{}")
        request=body.get("request") or {}
        request_id=body.get("request_id") or body.get("requestId")
        self.send_json({"success":True,"status":"ACCEPTED","pedido":request.get("pedido"),
                        "canal":body.get("canal"),"request_id":request_id})

ThreadingHTTPServer(("0.0.0.0", int(os.getenv("RECEIPT_STUB_PORT","9092"))), Handler).serve_forever()
