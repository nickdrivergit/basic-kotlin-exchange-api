#!/usr/bin/env bash
set -euo pipefail

# Wrapper to compile and run the signed-request helper.
# Usage:
#   scripts/run-helper.sh [-b BASE] [-k KEY] [-s SECRET]
# Or set env vars: API_BASE, API_KEY, API_SECRET

BASE=${API_BASE:-http://localhost:8080}
KEY=${API_KEY:-test-key}
SECRET=${API_SECRET:-test-secret}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -b|--base) BASE="$2"; shift 2;;
    -k|--key) KEY="$2"; shift 2;;
    -s|--secret) SECRET="$2"; shift 2;;
    -h|--help)
      echo "Usage: $0 [-b API_BASE] [-k API_KEY] [-s API_SECRET]"; exit 0;;
    *) echo "Unknown arg: $1"; exit 1;;
  esac
done

echo "Compiling helper..."
javac scripts/SignAndPost.java

echo "Running helper against ${BASE}..."
API_BASE="$BASE" API_KEY="$KEY" API_SECRET="$SECRET" \
  java -cp scripts SignAndPost

echo "Done. Check logs under scripts/logs/"

