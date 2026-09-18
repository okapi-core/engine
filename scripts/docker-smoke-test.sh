#!/usr/bin/env bash

set -Eeuo pipefail

WEB_URL="http://${OKAPI_WEB_HOST:-127.0.0.1}:${OKAPI_WEB_PORT:-9001}"
INGESTER_URL="http://${OKAPI_INGESTER_HOST:-127.0.0.1}:${OKAPI_INGESTER_PORT:-9009}"
OSCAR_URL="http://${OKAPI_OSCAR_HOST:-127.0.0.1}:${OKAPI_OSCAR_PORT:-9002}"
TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-90}"
INTERVAL_SECONDS="${SMOKE_INTERVAL_SECONDS:-2}"

dump_logs() {
  echo "Docker smoke test failed; collecting container state and logs." >&2
  docker ps -a --filter name=okapi-smoke --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' >&2 || true
  docker logs okapi-smoke-ingester >&2 || true
  docker logs okapi-smoke-web >&2 || true
  docker logs okapi-smoke-oscar >&2 || true
}

trap 'status=$?; if (( status != 0 )); then dump_logs; fi; exit "$status"' EXIT

wait_for_url() {
  local name="$1"
  local url="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  local body

  echo "Waiting for ${name} at ${url}..."
  while (( SECONDS < deadline )); do
    if body="$(curl --fail --silent --show-error --max-time 5 "$url" 2>/dev/null)"; then
      echo "${name} is ready: ${body}"
      return 0
    fi
    sleep "$INTERVAL_SECONDS"
  done

  echo "Timed out waiting for ${name} at ${url}" >&2
  return 1
}

assert_frontend() {
  local body

  body="$(curl --fail --silent --show-error --max-time 10 "${WEB_URL}/index.html")"
  if [[ "$body" != *"<html"* && "$body" != *"<!doctype"* && "$body" != *"<!DOCTYPE"* ]]; then
    echo "Frontend response does not look like an HTML document." >&2
    return 1
  fi
  echo "Frontend bundle is being served."
}

wait_for_url "ingester" "${INGESTER_URL}/health"
wait_for_url "web" "${WEB_URL}/internal/healthcheck"
wait_for_url "oscar" "${OSCAR_URL}/health"
assert_frontend

echo "Docker smoke test passed."
