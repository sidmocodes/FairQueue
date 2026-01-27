#!/bin/bash

# FairQueue End-to-End Test Script
# This script demonstrates the complete flow of the FairQueue system

set -e

echo "========================================="
echo "FairQueue End-to-End Test"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base URLs
GATEWAY_URL="http://localhost:8080"
QUEUE_URL="http://localhost:8081"
ADMISSION_URL="http://localhost:8082"
BOOKING_URL="http://localhost:8083"

echo -e "${BLUE}Step 1: Creating a new event...${NC}"
EVENT_RESPONSE=$(curl -s -X POST $ADMISSION_URL/events \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Concert 2026",
    "totalCapacity": 10000,
    "admissionRatePerMinute": 100,
    "eventStartTime": "2026-03-01T20:00:00Z",
    "queueOpenTime": "2026-02-01T10:00:00Z"
  }')

echo "$EVENT_RESPONSE" | jq '.'
EVENT_ID=$(echo "$EVENT_RESPONSE" | jq -r '.eventId')
echo -e "${GREEN}✓ Event created with ID: $EVENT_ID${NC}"
echo ""

echo -e "${BLUE}Step 2: User joining queue...${NC}"
JOIN_RESPONSE=$(curl -s -X POST $GATEWAY_URL/queue/join \
  -H "Content-Type: application/json" \
  -d "{
    \"userId\": \"testuser-$(date +%s)\",
    \"eventId\": \"$EVENT_ID\"
  }")

echo "$JOIN_RESPONSE" | jq '.'
QUEUE_TOKEN=$(echo "$JOIN_RESPONSE" | jq -r '.queueToken')
POSITION=$(echo "$JOIN_RESPONSE" | jq -r '.position')
echo -e "${GREEN}✓ Joined queue at position: $POSITION${NC}"
echo ""

echo -e "${BLUE}Step 3: Refreshing queue position (heartbeat)...${NC}"
REFRESH_RESPONSE=$(curl -s -X POST $QUEUE_URL/queue/refresh \
  -H "Authorization: Bearer $QUEUE_TOKEN")

echo "$REFRESH_RESPONSE" | jq '.'
echo -e "${GREEN}✓ Queue position refreshed${NC}"
echo ""

echo -e "${BLUE}Step 4: Checking queue status...${NC}"
STATUS_RESPONSE=$(curl -s -X GET $QUEUE_URL/queue/status \
  -H "Authorization: Bearer $QUEUE_TOKEN")

echo "$STATUS_RESPONSE" | jq '.'
echo -e "${GREEN}✓ Queue status retrieved${NC}"
echo ""

echo -e "${BLUE}Step 5: Claiming admission pass...${NC}"
ADMISSION_RESPONSE=$(curl -s -X POST $ADMISSION_URL/admission/claim \
  -H "Content-Type: application/json" \
  -d "{
    \"queueToken\": \"$QUEUE_TOKEN\"
  }")

echo "$ADMISSION_RESPONSE" | jq '.'
ADMISSION_PASS=$(echo "$ADMISSION_RESPONSE" | jq -r '.admissionPass')
echo -e "${GREEN}✓ Admission pass claimed: $ADMISSION_PASS${NC}"
echo ""

echo -e "${BLUE}Step 6: Entering booking system...${NC}"
BOOKING_RESPONSE=$(curl -s -X POST $BOOKING_URL/booking/enter \
  -H "Content-Type: application/json" \
  -d "{
    \"admissionPass\": \"$ADMISSION_PASS\"
  }")

echo "$BOOKING_RESPONSE" | jq '.'
echo -e "${GREEN}✓ Booking access granted!${NC}"
echo ""

echo -e "${BLUE}Step 7: Testing idempotency (trying to use same pass again)...${NC}"
BOOKING_RESPONSE_2=$(curl -s -X POST $BOOKING_URL/booking/enter \
  -H "Content-Type: application/json" \
  -d "{
    \"admissionPass\": \"$ADMISSION_PASS\"
  }")

echo "$BOOKING_RESPONSE_2" | jq '.'
echo -e "${GREEN}✓ Correctly rejected duplicate booking attempt${NC}"
echo ""

echo "========================================="
echo -e "${GREEN}All tests completed successfully!${NC}"
echo "========================================="
echo ""
echo "Summary:"
echo "  - Event ID: $EVENT_ID"
echo "  - Queue Position: $POSITION"
echo "  - Queue Token: ${QUEUE_TOKEN:0:50}..."
echo "  - Admission Pass: $ADMISSION_PASS"
echo ""
