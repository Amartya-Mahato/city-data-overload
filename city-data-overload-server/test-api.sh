#!/bin/bash

# City Data Overload API Testing Script
# Make sure the server is running on localhost:8080

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}🏙️  City Data Overload API Testing Script${NC}"
echo "========================================"
echo ""

# Test counter
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to run test
run_test() {
    local name="$1"
    local method="$2"
    local endpoint="$3"
    local data="$4"
    local expected_status="${5:-200}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "${YELLOW}Testing: $name${NC}"
    
    if [ "$data" == "" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method "$BASE_URL$endpoint" -H "Content-Type: application/json")
    else
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method "$BASE_URL$endpoint" -H "Content-Type: application/json" -d "$data")
    fi
    
    http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo $response | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$http_code" -eq "$expected_status" ]; then
        echo -e "${GREEN}✅ PASSED${NC} - Status: $http_code"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo "Response preview: $(echo $body | head -c 200)..."
    else
        echo -e "${RED}❌ FAILED${NC} - Expected: $expected_status, Got: $http_code"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo "Error: $body"
    fi
    echo ""
}

echo -e "${BLUE}Starting API Tests...${NC}"
echo ""

# 1. Health Check
run_test "Health Check" "GET" "/api/v1/flutter/health"

# 2. Test BigQuery
run_test "BigQuery Test" "GET" "/api/v1/flutter/test/bigquery?limit=5"

# 3. Get Areas
run_test "Get Bengaluru Areas" "GET" "/api/v1/flutter/bengaluru/areas"

# 4. Landing Page
run_test "Landing Page" "GET" "/"

# 5. API Documentation
run_test "API Documentation" "GET" "/api"

# 6. Comprehensive Map Data
run_test "Comprehensive Map Data" "POST" "/api/v1/flutter/map/comprehensive" '{
    "latitude": 12.9716,
    "longitude": 77.5946,
    "area": "MG Road",
    "radiusKm": 5.0,
    "maxResults": 20
}'

# 7. Simple Chat Query
run_test "Simple Chat Query" "POST" "/api/v1/flutter/chat/query" '{
    "query": "What is happening in Bengaluru today?",
    "userId": "test-user-123"
}'

# 8. Intelligent Chat
run_test "Intelligent Chat with Map Data" "POST" "/api/v1/flutter/chat/intelligent" '{
    "query": "Show me traffic alerts and events near MG Road",
    "latitude": 12.9716,
    "longitude": 77.5946,
    "area": "MG Road",
    "userId": "test-user-123"
}'

# 9. Events Synthesized
run_test "Synthesized Events" "POST" "/api/v1/flutter/events/synthesized" '{
    "latitude": 12.9352,
    "longitude": 77.6245,
    "area": "Koramangala",
    "radiusKm": 3.0,
    "maxResults": 15
}'

# 10. Predictive Alerts
run_test "Predictive Alerts" "POST" "/api/v1/flutter/alerts/predictive" '{
    "area": "Koramangala",
    "latitude": 12.9352,
    "longitude": 77.6245,
    "forecastHours": 24
}'

# 11. Mood Map Data
run_test "Mood Map Data" "POST" "/api/v1/flutter/map/mood" '{
    "areas": ["Koramangala", "MG Road", "HSR Layout", "Indiranagar"],
    "timeframe": "24h"
}'

# 12. Submit Citizen Report
run_test "Submit Citizen Report" "POST" "/api/v1/flutter/reports/submit" '{
    "userId": "test-user-456",
    "content": "Traffic jam due to construction work on Hosur Road near Electronic City",
    "latitude": 12.8456,
    "longitude": 77.6603,
    "area": "Electronic City",
    "ttlHours": 12
}'

# 13. Get User Report History
run_test "User Report History" "GET" "/api/v1/flutter/reports/history/test-user-456?limit=10"

# 14. Test Different Areas - Whitefield
run_test "Whitefield Area Test" "POST" "/api/v1/flutter/chat/intelligent" '{
    "query": "Any infrastructure issues in Whitefield?",
    "latitude": 12.9698,
    "longitude": 77.7500,
    "area": "Whitefield",
    "userId": "test-user-123"
}'

# 15. Traffic Specific Query
run_test "Traffic Alerts Query" "POST" "/api/v1/flutter/chat/intelligent" '{
    "query": "Show me all traffic alerts and road conditions",
    "latitude": 12.9716,
    "longitude": 77.5946,
    "area": "MG Road",
    "userId": "test-user-123"
}'

# 16. Emergency Query
run_test "Emergency Alerts Query" "POST" "/api/v1/flutter/chat/intelligent" '{
    "query": "Are there any emergency alerts or critical situations?",
    "latitude": 12.9352,
    "longitude": 77.6245,
    "area": "Koramangala",
    "userId": "test-user-123"
}'

# 17. Mood Analysis Query
run_test "Mood Analysis Query" "POST" "/api/v1/flutter/chat/intelligent" '{
    "query": "What is the general mood and sentiment in HSR Layout?",
    "latitude": 12.9082,
    "longitude": 77.6476,
    "area": "HSR Layout",
    "userId": "test-user-123"
}'

# 18. Predictive Query
run_test "Predictive Analysis Query" "POST" "/api/v1/flutter/chat/intelligent" '{
    "query": "What can we expect to happen in the next few hours?",
    "latitude": 12.9165,
    "longitude": 77.6761,
    "area": "Bellandur",
    "userId": "test-user-123"
}'

echo ""
echo -e "${BLUE}========================================"
echo "📊 Test Results Summary"
echo "========================================"
echo -e "Total Tests: $TOTAL_TESTS"
echo -e "${GREEN}✅ Passed: $PASSED_TESTS${NC}"
echo -e "${RED}❌ Failed: $FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed!${NC}"
else
    echo -e "${YELLOW}⚠️  Some tests failed. Check the output above.${NC}"
fi

echo ""
echo -e "${BLUE}💡 Tips:${NC}"
echo "1. Make sure the server is running on localhost:8080"
echo "2. Check BigQuery connectivity and data"
echo "3. Verify GCP credentials are properly configured"
echo "4. Look at server logs for detailed error information" 