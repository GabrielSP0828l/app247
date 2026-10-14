#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
COMPOSE_FILE="$ROOT_DIR/docker-compose.e2e.yml"
TERMINAL_PYTHON_ROOT=${TERMINAL_PYTHON_ROOT:-"$(cd "$ROOT_DIR/../TerminalPython" && pwd)"}
TERMINAL_PYTHON=${TERMINAL_PYTHON:-"$TERMINAL_PYTHON_ROOT/.venv/bin/python"}
E2E_RUNTIME_DIR=$(mktemp -d -t app247-e2e-terminal-XXXXXXXX)
E2E_LOG_DIR=$(mktemp -d -t app247-e2e-logs-XXXXXXXX)
BACKEND_PID=""

export E2E_DB_PASSWORD=${E2E_DB_PASSWORD:-$(openssl rand -hex 18)}
export E2E_DB_ROOT_PASSWORD=${E2E_DB_ROOT_PASSWORD:-$(openssl rand -hex 18)}
export E2E_JWT_SECRET=${E2E_JWT_SECRET:-$(openssl rand -hex 32)}
export E2E_IDENTITY_HASH_SECRET=${E2E_IDENTITY_HASH_SECRET:-$(openssl rand -hex 32)}
export E2E_TERMINAL_INTERNAL_TOKEN=${E2E_TERMINAL_INTERNAL_TOKEN:-$(openssl rand -hex 32)}
export E2E_MP_ACCESS_TOKEN=${E2E_MP_ACCESS_TOKEN:-$(openssl rand -hex 24)}
export E2E_MP_REFRESH_TOKEN=${E2E_MP_REFRESH_TOKEN:-$(openssl rand -hex 24)}
export E2E_RECEIPT_TOKEN=${E2E_RECEIPT_TOKEN:-$(openssl rand -hex 24)}
export E2E_COMPOSE_FILE="$COMPOSE_FILE"
export E2E_RUNTIME_DIR
export TERMINAL_PYTHON_ROOT
export API_URL=http://127.0.0.1:38080
export WS_URL=ws://127.0.0.1:38080
export POINT_STUB_URL=http://127.0.0.1:39091
export DEVICE_CREDENTIAL_PATH="$E2E_RUNTIME_DIR/device-credential"
export TERMINAL_INTERNAL_TOKEN="$E2E_TERMINAL_INTERNAL_TOKEN"
export LEGACY_TERMINAL_AUTH_ENABLED=true
export QT_QPA_PLATFORM=offscreen

cleanup() {
  status=$?
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ $status -ne 0 && -f "$E2E_LOG_DIR/backend.log" ]]; then
    echo "[E2E] backend log (últimas linhas):" >&2
    tail -n 120 "$E2E_LOG_DIR/backend.log" >&2
  fi
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf -- "$E2E_RUNTIME_DIR" "$E2E_LOG_DIR"
  exit "$status"
}
trap cleanup EXIT INT TERM

wait_health() {
  # A primeira execução pode precisar compilar backend e testes antes do boot.
  # A raiz responde assim que o HTTP está pronto; o health agregado pode aguardar
  # probes externos e por isso é validado uma única vez, com timeout próprio.
  for _ in $(seq 1 300); do
    if curl -fsS --max-time 2 "$API_URL/" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "[E2E] Spring não ficou saudável" >&2
  return 1
}

validate_health() {
  local health
  for _ in $(seq 1 30); do
    health=$(curl -sS --max-time 10 "$API_URL/actuator/health" 2>/dev/null || true)
    if [[ "$health" == *'"status":"UP"'* ]]; then
      return 0
    fi
    sleep 1
  done
  echo "[E2E] health agregado não retornou UP" >&2
  return 1
}

start_backend() {
  SPRING_PROFILES_ACTIVE=prod PORT=38080 \
  DB_URL='jdbc:mysql://127.0.0.1:33306/app247_e2e?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true' \
  DB_USERNAME=app247_e2e DB_PASSWORD="$E2E_DB_PASSWORD" \
  REDIS_HOST=127.0.0.1 REDIS_PORT=36379 \
  JWT="$E2E_JWT_SECRET" IDENTITY_HASH_SECRET="$E2E_IDENTITY_HASH_SECRET" \
  TERMINAL_INTERNAL_TOKEN="$E2E_TERMINAL_INTERNAL_TOKEN" TERMINAL_LEGACY_AUTH_ENABLED=true \
  MP_API_BASE_URL=http://127.0.0.1:39091 \
  COMPROVANTE_SERVICE_URL=http://127.0.0.1:39092 \
  COMPROVANTE_INTERNAL_TOKEN="$E2E_RECEIPT_TOKEN" \
  WEBSOCKET_ALLOWED_ORIGINS=http://127.0.0.1:38080 \
  HTTP_CONNECT_TIMEOUT=1s HTTP_READ_TIMEOUT=2s \
  PAYMENT_RECONCILIATION_DELAY_MS=600000 PAYMENT_RECONCILIATION_INITIAL_DELAY_MS=600000 \
  PAYMENT_SUBMISSION_RETRY_COOLDOWN_MS=5000 TEST_ENDPOINTS_ENABLED=false \
  "$ROOT_DIR/mvnw" -f "$ROOT_DIR/pom.xml" spring-boot:run >>"$E2E_LOG_DIR/backend.log" 2>&1 &
  BACKEND_PID=$!
  wait_health
  validate_health
}

[[ -x "$TERMINAL_PYTHON" ]] || { echo "Python do Terminal não encontrado: $TERMINAL_PYTHON" >&2; exit 1; }

docker compose -f "$COMPOSE_FILE" up -d --wait
start_backend

{
  printf "SET @mp_access='%s'; SET @mp_refresh='%s';\n" "$E2E_MP_ACCESS_TOKEN" "$E2E_MP_REFRESH_TOKEN"
  sed -n '1,$p' "$ROOT_DIR/scripts/e2e/fixtures.sql"
} | docker compose -f "$COMPOSE_FILE" exec -T mysql-e2e \
      mysql -uapp247_e2e -p"$E2E_DB_PASSWORD" app247_e2e

"$TERMINAL_PYTHON" "$ROOT_DIR/scripts/e2e/terminal_backend_harness.py"
"$TERMINAL_PYTHON" "$ROOT_DIR/scripts/e2e/customer_qr_harness.py"
"$TERMINAL_PYTHON" "$ROOT_DIR/scripts/e2e/backend_restart_probe.py" prepare

kill "$BACKEND_PID"
wait "$BACKEND_PID" || true
BACKEND_PID=""
start_backend
"$TERMINAL_PYTHON" "$ROOT_DIR/scripts/e2e/backend_restart_probe.py" recover

secrets=(
  "$E2E_TERMINAL_INTERNAL_TOKEN"
  "$E2E_DB_PASSWORD"
  "$E2E_DB_ROOT_PASSWORD"
  "$E2E_JWT_SECRET"
  "$E2E_IDENTITY_HASH_SECRET"
  "$E2E_MP_ACCESS_TOKEN"
  "$E2E_MP_REFRESH_TOKEN"
  "$E2E_RECEIPT_TOKEN"
)
if [[ -f "$DEVICE_CREDENTIAL_PATH" ]]; then
  secrets+=("$(<"$DEVICE_CREDENTIAL_PATH")")
fi

for secret in "${secrets[@]}"; do
  if grep -Fq -- "$secret" "$E2E_LOG_DIR/backend.log"; then
    echo "[E2E] segredo encontrado no log do backend" >&2
    exit 1
  fi
done

echo "[E2E] TERMINAL ↔ BACKEND: cadeia obrigatória aprovada"
