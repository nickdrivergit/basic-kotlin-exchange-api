#!/usr/bin/env bash
set -euo pipefail

API_URL="http://localhost:8080/api"
SYMBOL="BTCZAR"

# how many random orders to place
COUNT=${1:-20}

echo "Placing $COUNT random orders into $SYMBOL..."

for i in $(seq 1 $COUNT); do
  SIDE=$([ $((RANDOM % 2)) -eq 0 ] && echo "BUY" || echo "SELL")

  # random price around 500k ± 20k
  PRICE=$((480000 + RANDOM % 40000)).00

  # random quantity 0.001 – 0.01
  QUANTITY="0.00$((RANDOM % 9 + 1))"

  echo "[$i] $SIDE $QUANTITY @ $PRICE"

  curl -s -X POST "$API_URL/orders/$SYMBOL" \
    -H "Content-Type: application/json" \
    -d "{\"side\":\"$SIDE\",\"price\":\"$PRICE\",\"quantity\":\"$QUANTITY\"}" \
    | jq '.'
done

echo
echo "=== FINAL ORDERBOOK ==="
curl -s "$API_URL/orderbook/$SYMBOL" | jq '.'

echo
echo "=== RECENT TRADES ==="
curl -s "$API_URL/trades/$SYMBOL?limit=10" | jq '.'
