#!/usr/bin/env sh
# End-to-end check of the MCP endpoint, calling it the way an AI client does (JSON-RPC over HTTP).
# Usage: ./scripts/mcp-smoke.sh [base_url]
set -eu
BASE=${1:-http://localhost:8080}
EMAIL="mcp$(date +%s)@example.com"
field() { sed -n "s/.*\"$1\" *: *\"\([^\"]*\)\".*/\1/p" | head -1; }
fail() { echo "FAIL: $*"; exit 1; }

curl -sf -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Mcp\",\"email\":\"$EMAIL\",\"password\":\"secret123\"}" >/dev/null || fail signup
TOKEN=$(curl -sf -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"secret123\"}" | field token)
[ -n "$TOKEN" ] || fail login

rpc() { curl -sf -X POST "$BASE/mcp" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' -d "$1"; }
call() { rpc "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"$1\",\"arguments\":$2}}"; }

rpc '{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"1"}}}' \
  | grep -q '"protocolVersion":"2025-06-18"' || fail initialize
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/mcp" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","method":"notifications/initialized"}')
[ "$CODE" = 202 ] || fail "notification should get 202, got $CODE"
rpc '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | grep -q 'create_app_user' || fail tools/list
echo "ok  initialize + tools/list"

P1=$(call create_project '{"name":"todo-app"}')
ID=$(echo "$P1" | tr -d '\\' | field id)
echo "$P1" | grep -q 'created\\" : true' || fail "create_project: $P1"
call create_project '{"name":"todo-app"}' | grep -q 'created\\" : false' || fail "create_project should be idempotent"
echo "ok  create_project (and a retry returns the same project)"

call insert_document "{\"project_id\":\"$ID\",\"collection\":\"todos\",\"data\":{\"title\":\"ship MCP\",\"done\":false}}" \
  | grep -q '"isError":false' || fail insert_document
call insert_document "{\"project_id\":\"$ID\",\"collection\":\"todos\",\"data\":{\"title\":\"record demo\",\"done\":true}}" >/dev/null
Q=$(call query_documents "{\"project_id\":\"$ID\",\"collection\":\"todos\",\"where\":{\"done\":false}}")
echo "$Q" | grep -q 'ship MCP' && ! echo "$Q" | grep -q 'record demo' || fail "query_documents filter: $Q"
call list_collections "{\"project_id\":\"$ID\"}" | grep -q 'todos' || fail list_collections
echo "ok  insert_document + query_documents (with filter) + list_collections"

call create_app_user "{\"project_id\":\"$ID\",\"name\":\"Ana\",\"email\":\"ana@example.com\",\"password\":\"secret123\"}" \
  | grep -q '"isError":false' || fail create_app_user
KEY=$(call get_api_key "{\"project_id\":\"$ID\"}" | tr -d '\\' | field apiKey)
curl -sf -X POST "$BASE/sdk/auth/login" -H "x-api-key: $KEY" -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"secret123"}' | grep -q token || fail "app user login with the key"
curl -sf "$BASE/api/$ID/todos" -H "x-api-key: $KEY" | grep -q 'ship MCP' || fail "data API with the key"
echo "ok  create_app_user + get_api_key (app user logs in, data API works with the key)"

# A second developer must not reach the first developer's project
EMAIL2="other$(date +%s)@example.com"
curl -sf -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Other\",\"email\":\"$EMAIL2\",\"password\":\"secret123\"}" >/dev/null
TOKEN=$(curl -sf -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL2\",\"password\":\"secret123\"}" | field token)
call get_api_key "{\"project_id\":\"$ID\"}" | grep -q '"isError":true' || fail "other developer got the API key"
echo "ok  another developer can't read the project's key"

CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/mcp" -H 'Content-Type: application/json' -d '{}')
[ "$CODE" = 401 ] || [ "$CODE" = 403 ] || fail "no token should be rejected, got $CODE"
echo "ok  no token is rejected ($CODE)"
echo "ALL PASSED"
