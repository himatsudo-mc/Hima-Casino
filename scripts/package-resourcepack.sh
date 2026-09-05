#!/usr/bin/env bash
# Packages resource-pack/ into target/hima-casino-resource-pack.zip for distribution.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR/resource-pack"
OUT_DIR="$ROOT_DIR/target"
OUT_FILE="$OUT_DIR/hima-casino-resource-pack.zip"

mkdir -p "$OUT_DIR"
rm -f "$OUT_FILE"

cd "$SRC_DIR"
zip -qr "$OUT_FILE" . -x ".*"

echo "Created $OUT_FILE"
