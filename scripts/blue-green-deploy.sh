#!/usr/bin/env bash

set -Eeuo pipefail

APP_DIR="$HOME/SeniorON_BE"
STATE_FILE="$APP_DIR/.current-color"

IMAGE_TAG="${IMAGE_TAG:?IMAGE_TAG is required}"

cd "$APP_DIR"

echo "=========================================="
echo " SeniorON Blue-Green Deployment"
echo " IMAGE_TAG: $IMAGE_TAG"
echo "=========================================="

# --------------------------------------------------
# Utility Functions
# --------------------------------------------------

get_container_id() {
    docker compose ps -q "$1"
}

get_health_status() {
    local service="$1"
    local container_id

    container_id=$(get_container_id "$service")

    if [[ -z "$container_id" ]]; then
        echo "not-found"
        return
    fi

    docker inspect \
        --format='{{.State.Health.Status}}' \
        "$container_id" 2>/dev/null || echo "unknown"
}

wait_for_health() {
    local service="$1"

    echo ""
    echo "Waiting for $service health..."

    for i in $(seq 1 30); do
        local status
        status=$(get_health_status "$service")

        echo "[$i/30] $service health=$status"

        if [[ "$status" == "healthy" ]]; then
            echo "$service is healthy."
            return 0
        fi

        if [[ "$status" == "unhealthy" ]]; then
            echo "ERROR: $service became unhealthy."
            return 1
        fi

        sleep 5
    done

    echo "ERROR: $service health check timeout."
    return 1
}

cleanup_service() {
    local service="$1"

    docker compose stop "$service" || true
    docker compose rm -f "$service" || true
}

switch_upstream() {
    local color="$1"
    local src="nginx/upstream.$color.conf"

    if [[ ! -f "$src" ]]; then
        echo "ERROR: upstream config not found: $src"
        return 1
    fi

    if ! cp "$src" "nginx/conf.d/upstream.conf"; then
        echo "ERROR: Failed to copy upstream config for $color."
        return 1
    fi

    echo "Testing Nginx configuration..."

    if ! docker compose exec -T nginx nginx -t; then
        echo "ERROR: Nginx configuration test failed."
        return 1
    fi

    echo "Reloading Nginx..."

    if ! docker compose exec -T nginx nginx -s reload; then
        echo "ERROR: Nginx reload failed."
        return 1
    fi

    if ! grep -q "app-$color:8080" "nginx/conf.d/upstream.conf"; then
        echo "ERROR: Active upstream does not point to app-$color."
        return 1
    fi

    return 0
}

rollback_upstream() {
    local color="$1"
    local src="nginx/upstream.$color.conf"

    echo ""
    echo "=========================================="
    echo " Rolling back traffic to $color"
    echo "=========================================="

    if [[ ! -f "$src" ]]; then
        echo "CRITICAL: Rollback upstream config not found: $src"
        return 1
    fi

    if ! cp "$src" "nginx/conf.d/upstream.conf"; then
        echo "CRITICAL: Failed to copy rollback upstream config."
        return 1
    fi

    if ! grep -q "app-$color:8080" "nginx/conf.d/upstream.conf"; then
        echo "CRITICAL: Rollback upstream does not point to app-$color."
        return 1
    fi

    if ! docker compose exec -T nginx nginx -t; then
        echo "CRITICAL: Rollback Nginx configuration test FAILED."
        return 1
    fi

    if ! docker compose exec -T nginx nginx -s reload; then
        echo "CRITICAL: Rollback Nginx reload FAILED."
        return 1
    fi

    echo "Traffic restored to $color."
    return 0
}

smoke_test() {
    echo ""
    echo "Running external smoke test..."

    for i in $(seq 1 10); do

        RESPONSE=$(curl \
            -fsS \
            --max-time 10 \
            https://senioron.site/actuator/health/readiness \
            2>/dev/null || true)

        if echo "$RESPONSE" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
            echo "[$i/10] Smoke test passed."
            return 0
        fi

        echo "[$i/10] Smoke test failed. Retrying..."
        sleep 3
    done

    echo "ERROR: Smoke test failed."
    return 1
}

# --------------------------------------------------
# 1. 현재 운영 색상 확인
# --------------------------------------------------

if [[ -f "$STATE_FILE" ]]; then
    CURRENT_STATE=$(cat "$STATE_FILE")

    CURRENT_COLOR="${CURRENT_STATE%%:*}"
    CURRENT_TAG="${CURRENT_STATE#*:}"
else
    CURRENT_COLOR="none"
    CURRENT_TAG="none"
fi

case "$CURRENT_COLOR" in

    blue)
        CURRENT_SERVICE="app-blue"
        NEXT_COLOR="green"
        NEXT_SERVICE="app-green"
        ;;

    green)
        CURRENT_SERVICE="app-green"
        NEXT_COLOR="blue"
        NEXT_SERVICE="app-blue"
        ;;

    none)
        CURRENT_SERVICE=""
        NEXT_COLOR="blue"
        NEXT_SERVICE="app-blue"
        ;;

    *)
        echo "ERROR: Invalid deployment state: $CURRENT_STATE"
        exit 1
        ;;

esac

echo "Current : $CURRENT_COLOR ($CURRENT_TAG)"
echo "Next    : $NEXT_COLOR ($IMAGE_TAG)"

# --------------------------------------------------
# 2. 새 이미지 Pull
# --------------------------------------------------

echo ""
echo "[1/7] Pulling $NEXT_SERVICE image..."

IMAGE_TAG="$IMAGE_TAG" \
docker compose pull "$NEXT_SERVICE"

# --------------------------------------------------
# 3. 새 애플리케이션 기동
# --------------------------------------------------

echo ""
echo "[2/7] Starting $NEXT_SERVICE..."

IMAGE_TAG="$IMAGE_TAG" \
docker compose up -d "$NEXT_SERVICE"

# --------------------------------------------------
# 4. Health Check
# --------------------------------------------------

echo ""
echo "[3/7] Health Check..."

if ! wait_for_health "$NEXT_SERVICE"; then

    echo ""
    echo "ERROR: New application failed health check."

    docker compose logs --tail=100 "$NEXT_SERVICE" || true

    cleanup_service "$NEXT_SERVICE"

    exit 1
fi

# --------------------------------------------------
# 최초 Bootstrap
# --------------------------------------------------

if [[ "$CURRENT_COLOR" == "none" ]]; then

    echo ""
    echo "=========================================="
    echo " Initial Blue Bootstrap"
    echo "=========================================="

    echo "Preparing Nginx upstream for $NEXT_COLOR..."

    cp \
        "nginx/upstream.$NEXT_COLOR.conf" \
        "nginx/conf.d/upstream.conf"

    echo "Starting Nginx..."

    docker compose up -d nginx

    sleep 3

    echo "Testing Nginx configuration..."

    if ! docker compose exec -T nginx nginx -t; then
        echo "ERROR: Initial Nginx configuration test failed."

        cleanup_service "$NEXT_SERVICE"

        exit 1
    fi

    echo "Reloading Nginx..."

    if ! docker compose exec -T nginx nginx -s reload; then
        echo "ERROR: Initial Nginx reload failed."

        cleanup_service "$NEXT_SERVICE"

        exit 1
    fi

    echo ""
    echo "Running initial smoke test..."

    if ! smoke_test; then
        echo ""
        echo "ERROR: Initial bootstrap smoke test failed."

        cleanup_service "$NEXT_SERVICE"

        exit 1
    fi

    echo "$NEXT_COLOR:$IMAGE_TAG" > "$STATE_FILE"

    echo ""
    echo "=========================================="
    echo " Initial Bootstrap Successful"
    echo " Active : $NEXT_COLOR"
    echo " Image  : $IMAGE_TAG"
    echo "=========================================="

    exit 0
fi

# --------------------------------------------------
# 5. Nginx Upstream 전환
# --------------------------------------------------

echo ""
echo "[4/7] Switching Nginx upstream..."

if ! switch_upstream "$NEXT_COLOR"; then

    echo ""
    echo "ERROR: Nginx switch failed."
    echo "Restoring previous environment..."

    if ! rollback_upstream "$CURRENT_COLOR"; then
        echo ""
        echo "CRITICAL: Automatic rollback failed."
        echo "Manual intervention is required."
    fi

    cleanup_service "$NEXT_SERVICE"

    exit 1
fi

echo "Traffic switched to $NEXT_SERVICE."

# --------------------------------------------------
# 6. External Smoke Test
# --------------------------------------------------

echo ""
echo "[5/7] External Smoke Test..."

if ! smoke_test; then

    echo ""
    echo "ERROR: Smoke test failed."
    echo "Rolling back..."

    if ! rollback_upstream "$CURRENT_COLOR"; then
        echo ""
        echo "CRITICAL: Automatic rollback failed."
        echo "Manual intervention is required."
    fi

    cleanup_service "$NEXT_SERVICE"

    exit 1
fi

echo "Smoke test passed."

# --------------------------------------------------
# 7. 운영 상태 기록 및 이전 환경 종료
# --------------------------------------------------

echo ""
echo "[6/7] Updating deployment state..."

echo "$NEXT_COLOR:$IMAGE_TAG" > "$STATE_FILE"

echo ""
echo "[7/7] Stopping old application..."

cleanup_service "$CURRENT_SERVICE"

echo ""
echo "=========================================="
echo " Deployment Successful"
echo "=========================================="
echo " Previous : $CURRENT_COLOR ($CURRENT_TAG)"
echo " Active   : $NEXT_COLOR"
echo " Image    : $IMAGE_TAG"
echo "=========================================="