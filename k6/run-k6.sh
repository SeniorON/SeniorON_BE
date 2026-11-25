#!/usr/bin/env bash
# =============================================================
#  SeniorON k6 부하테스트 실행 스크립트
#  결과를 InfluxDB에 전송하여 Grafana 대시보드에서 실시간 모니터링
# =============================================================

set -euo pipefail

# ── 설정 ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

K6_SCRIPT="${SCRIPT_DIR}/load-test.js"
INFLUXDB_URL="http://influxdb:8086/k6"

# Docker Compose 네트워크 자동 감지
NETWORK=$(docker compose ps -q app | xargs docker inspect \
  -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}')

# ── 사전 검사 ─────────────────────────────────────────────────
if [ ! -f "$K6_SCRIPT" ]; then
    echo "❌ k6 스크립트를 찾을 수 없습니다: $K6_SCRIPT"
    exit 1
fi

echo "============================================="
echo "  🚀 SeniorON k6 부하테스트 시작"
echo "============================================="
echo "  스크립트  : $K6_SCRIPT"
echo "  InfluxDB  : $INFLUXDB_URL"
echo "  네트워크  : $NETWORK"
echo "============================================="
echo ""

# ── k6 실행 (Docker) ─────────────────────────────────────────
docker run --rm -i \
    --network "$NETWORK" \
    -v "${SCRIPT_DIR}:/scripts" \
    grafana/k6:latest run \
    --out influxdb="$INFLUXDB_URL" \
    /scripts/load-test.js

echo ""
echo "============================================="
echo "  ✅ 부하테스트 완료!"
echo "  📊 Grafana 대시보드에서 결과를 확인하세요."
echo "============================================="
