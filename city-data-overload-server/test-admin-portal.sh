#!/bin/bash

# Admin Portal Testing Script
# Tests all admin endpoints and functionality

BASE_URL="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}🏗️  City Data Overload - Admin Portal Testing${NC}"
echo "=================================================="
echo ""

# Test counter
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to run admin test
run_admin_test() {
    local name="$1"
    local method="$2"
    local endpoint="$3"
    local data="$4"
    local expected_status="${5:-200}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    echo -e "${YELLOW}Testing Admin: $name${NC}"
    
    if [ "$data" == "" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method "$BASE_URL$endpoint" -H "Content-Type: application/json")
    else
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X $method "$BASE_URL$endpoint" -H "Content-Type: application/json" -d "$data")
    fi
    
    http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo $response | sed -e 's/HTTPSTATUS\:.*//g')
    
    if [ "$http_code" -eq "$expected_status" ]; then
        echo -e "${GREEN}✅ ADMIN PASSED${NC} - Status: $http_code"
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo "Response preview: $(echo $body | head -c 150)..."
    else
        echo -e "${RED}❌ ADMIN FAILED${NC} - Expected: $expected_status, Got: $http_code"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo "Error: $body"
    fi
    echo ""
}

echo -e "${BLUE}Starting Admin Portal Tests...${NC}"
echo ""

# 1. Admin Portal Landing Page
run_admin_test "Admin Portal Landing" "GET" "/admin/"

# 2. Admin Dashboard Access
run_admin_test "Admin Dashboard" "GET" "/admin/dashboard.html"

# 3. System Analytics Overview
run_admin_test "System Analytics Overview" "GET" "/admin/api/analytics/overview"

# 4. Event Analytics
run_admin_test "Event Analytics (7 days)" "GET" "/admin/api/analytics/events?days=7"

# 5. BigQuery Events Data
run_admin_test "BigQuery Events Data" "GET" "/admin/api/data/bigquery/events?limit=10"

# 6. Firestore Events Data
run_admin_test "Firestore Events Data" "GET" "/admin/api/data/firestore/events?area=Koramangala&limit=10"

# 7. Agent Status
run_admin_test "Agent Status Check" "GET" "/admin/api/system/agents/status"

# 8. System Cleanup
run_admin_test "System Cleanup" "POST" "/admin/api/system/cleanup"

# 9. User Data Retrieval
run_admin_test "User Data Retrieval" "GET" "/admin/api/data/users/test-user-123/reports?limit=5"

# 10. Event Deletion (Mock)
run_admin_test "Event Deletion" "DELETE" "/admin/api/data/events/mock-event-id"

# 11. User Report Deletion
run_admin_test "User Report Deletion" "DELETE" "/admin/api/data/users/test-user-123/reports/mock-report-id"

echo ""
echo -e "${BLUE}=================================================="
echo "📊 Admin Portal Test Results Summary"
echo "=================================================="
echo -e "Total Admin Tests: $TOTAL_TESTS"
echo -e "${GREEN}✅ Passed: $PASSED_TESTS${NC}"
echo -e "${RED}❌ Failed: $FAILED_TESTS${NC}"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}🎉 All admin tests passed!${NC}"
else
    echo -e "${YELLOW}⚠️  Some admin tests failed. Check the output above.${NC}"
fi

echo ""
echo -e "${BLUE}🚀 Admin Portal Access:${NC}"
echo "Main Portal: $BASE_URL/admin/"
echo "Dashboard:   $BASE_URL/admin/dashboard.html"
echo ""
echo -e "${BLUE}💡 Admin Tips:${NC}"
echo "1. Access the admin dashboard through your browser"
echo "2. All admin endpoints are under /admin/api/"
echo "3. Use the interactive dashboard for better UX"
echo "4. Check server logs for detailed admin operation info"
echo "5. Admin portal includes real-time data management"

echo ""
echo -e "${BLUE}📊 Available Admin Features:${NC}"
echo "• System Dashboard with real-time metrics"
echo "• Advanced Analytics and Trend Analysis"
echo "• BigQuery and Firestore Data Management"
echo "• User Management and Report Control"
echo "• Agent Health Monitoring and Control"
echo "• System Cleanup and Maintenance Tools"
echo "• Comprehensive System Reports"

echo ""
echo -e "${GREEN}Admin Portal is ready for use! 🎉${NC}" 