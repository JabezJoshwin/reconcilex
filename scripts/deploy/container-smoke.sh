#!/bin/sh
set -eu
: "${IMAGE:=reconcilex:ci}"
: "${DEPLOY_SHA:=local}"
RX_NAME="rx-ci-$$"
RX_PASSWORD="$(openssl rand -hex 24)"
cleanup() {
  docker rm -f "$RX_NAME-app" "$RX_NAME-db" >/dev/null 2>&1 || true
  docker network rm "$RX_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM
docker network create "$RX_NAME" >/dev/null
docker run -d --name "$RX_NAME-db" --network "$RX_NAME" -e POSTGRES_DB=reconcilex -e POSTGRES_USER=reconcilex -e POSTGRES_PASSWORD="$RX_PASSWORD" postgres:17-alpine >/dev/null
i=0
until docker exec "$RX_NAME-db" pg_isready -U reconcilex >/dev/null 2>&1; do
  i=$((i+1)); [ "$i" -lt 60 ] || exit 1; sleep 1
done
docker run -d --name "$RX_NAME-app" --network "$RX_NAME" --memory=512m -p 127.0.0.1::8080 \
  -e SPRING_PROFILES_ACTIVE=demo -e SERVER_ADDRESS=0.0.0.0 -e PORT=8080 \
  -e APP_BASE_URL=https://fixture.onrender.com -e CLERK_ISSUER=https://fixture.clerk.accounts.dev \
  -e CLERK_PUBLISHABLE_KEY=pk_test_Zml4dHVyZS5jbGVyay5hY2NvdW50cy5kZXYk \
  -e DB_URL="jdbc:postgresql://$RX_NAME-db:5432/reconcilex" -e DB_USER=reconcilex -e DB_PASSWORD="$RX_PASSWORD" \
  -e RENDER_GIT_COMMIT="$DEPLOY_SHA" "$IMAGE" >/dev/null
RX_ADDRESS="$(docker port "$RX_NAME-app" 8080/tcp)"
APP_URL="http://$RX_ADDRESS"
export APP_URL DEPLOY_SHA
i=0
until curl -fsS "$APP_URL/api/health" >/dev/null 2>&1; do
  i=$((i+1)); [ "$i" -lt 120 ] || { docker logs "$RX_NAME-app"; exit 1; }; sleep 1
done
node scripts/deploy/smoke.cjs --local
# All three schema migrations must exist in the actual container database.
RX_MIGRATIONS="$(docker exec "$RX_NAME-db" psql -U reconcilex -d reconcilex -Atc 'SELECT COUNT(*) FROM flyway_schema_history WHERE success')"
[ "$RX_MIGRATIONS" -ge 3 ]
