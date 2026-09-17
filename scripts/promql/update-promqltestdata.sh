#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
UPSTREAM_URL="https://github.com/prometheus/prometheus.git"
UPSTREAM_REF="main"
WORK_DIR="${ROOT_DIR}/.cache/prometheus-promqltest"
DEST_DIR="${ROOT_DIR}/okapi-promql/src/test/resources/promqltest/testdata"

if [[ ! -d "${WORK_DIR}/.git" ]]; then
  git clone --filter=blob:none --no-checkout "${UPSTREAM_URL}" "${WORK_DIR}"
  git -C "${WORK_DIR}" sparse-checkout init --cone
  git -C "${WORK_DIR}" sparse-checkout set promql/promqltest/testdata
fi

git -C "${WORK_DIR}" fetch origin "${UPSTREAM_REF}"
git -C "${WORK_DIR}" checkout --detach "origin/${UPSTREAM_REF}"

mkdir -p "${DEST_DIR}"
rsync -a --delete "${WORK_DIR}/promql/promqltest/testdata/" "${DEST_DIR}/"
git -C "${WORK_DIR}" rev-parse HEAD > "${DEST_DIR}/.upstream-commit"

echo "Updated promql testdata to $(cat "${DEST_DIR}/.upstream-commit")"
