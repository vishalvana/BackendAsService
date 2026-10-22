#!/usr/bin/env sh
# End-to-end check against a running instance: signup -> login -> project -> data -> tenant isolation.
# Usage: ./scripts/smoke.sh [base_url]
set -eu
BASE=${1:-http://localhost:8080}
EMAIL="smoke$(date +%s)@example.com"
field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }
fail() { echo "FAIL: $*"; exit 1; }

curl -sf -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Smoke\",\"email\":\"$EMAIL\",\"password\":\"secret123\"}" >/dev/null || fail signup
TOKEN=$(curl -sf -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"secret123\"}" | field token)
[ -n "$TOKEN" ] || fail login
echo "ok  signup + login"

project() { curl -sf -X POST "$BASE/projects" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"name\":\"$1\"}"; }
A=$(project todo-a); B=$(project todo-b)
A_ID=$(echo "$A" | field id); A_KEY=$(echo "$A" | field apiKey)
B_ID=$(echo "$B" | field id)
[ -n "$A_ID" ] && [ -n "$A_KEY" ] && [ -n "$B_ID" ] || fail "create project: $A"
echo "ok  create 2 projects"

curl -sf -X POST "$BASE/api/$A_ID/todos" -H "x-api-key: $A_KEY" -H 'Content-Type: application/json' \
  -d '{"title":"ship week 1","done":false}' >/dev/null || fail "insert record"
curl -sf "$BASE/api/$A_ID/todos" -H "x-api-key: $A_KEY" | grep -q 'ship week 1' || fail "query records"
echo "ok  insert + query with API key"

CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/$B_ID/todos" -H "x-api-key: $A_KEY")
[ "$CODE" = 403 ] || fail "project A's key read project B's data (HTTP $CODE)"
echo "ok  API key cannot touch another project (403)"
echo "ALL PASSED"
