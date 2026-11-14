#!/usr/bin/env bash
# 사용: load-test/ch02/run.sh <결제 서버 jar> <Mock PG jar> <결과 이름>
# 같은 머신에서 MySQL(Docker), Mock PG, 결제 서버, k6 를 차례로 띄운다. 회차마다 DB 를 새로 만든다.
set -euo pipefail
APP_JAR=$1; PG_JAR=$2; NAME=$3
OUT="$(cd "$(dirname "$0")" && pwd)/results"; mkdir -p "$OUT"
SCRIPT="$(cd "$(dirname "$0")" && pwd)/lookup-while-confirming.js"

docker rm -f ch02-mysql >/dev/null 2>&1 || true
docker run -d --name ch02-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=payment \
  -e MYSQL_USER=payment -e MYSQL_PASSWORD=payment -p 3317:3306 mysql:8.4 >/dev/null
until docker exec ch02-mysql mysql -upayment -ppayment -e 'select 1' payment >/dev/null 2>&1; do sleep 1; done

java -jar "$PG_JAR" >"$OUT/$NAME-mock-pg.log" 2>&1 & PG_PID=$!
java -jar "$APP_JAR" --spring.datasource.url=jdbc:mysql://localhost:3317/payment \
  --spring.datasource.username=payment --spring.datasource.password=payment >"$OUT/$NAME-app.log" 2>&1 & APP_PID=$!
trap 'kill $APP_PID $PG_PID 2>/dev/null || true; docker rm -f ch02-mysql >/dev/null 2>&1 || true' EXIT
until curl -s -o /dev/null http://localhost:8090/control/approvals/none; do sleep 1; done
until curl -s -o /dev/null http://localhost:8080/api/v1/payments/none; do sleep 1; done

curl -s -X PUT http://localhost:8090/control/scenario -H 'Content-Type: application/json' \
  -d '{"delayMillis":1000,"outcome":"APPROVE"}'

# 예열 10초(결과는 버린다) 뒤 본 측정 30초
k6 run --quiet -e DURATION=10s "$SCRIPT" >/dev/null 2>&1
k6 run -e DURATION=30s --summary-export "$OUT/$NAME-summary.json" "$SCRIPT" >"$OUT/$NAME-k6.txt" 2>&1
