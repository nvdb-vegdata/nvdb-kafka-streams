#!/usr/bin/env bash

# Check lag every 5 seconds until caught up
while true; do
  RESPONSE=$(curl -s http://localhost:8080/api/nvdb/streams/lag)
  IS_UP_TO_DATE=$(echo "$RESPONSE" | jq -r '.isUpToDate')

  if [ "$IS_UP_TO_DATE" = "true" ]; then
    echo "✅ Processing is up-to-date!"
    break
  fi

  TOTAL_LAG=$(echo "$RESPONSE" | jq '[.topics[].totalLag] | add')
  echo "⏳ Still processing... Total lag: $TOTAL_LAG"
  sleep 5
done
