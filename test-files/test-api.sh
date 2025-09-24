#!/bin/bash

# Test script for Document Extraction Service API
# This script demonstrates how to upload a ZIP file and retrieve results

echo "🚀 Document Extraction Service API Test"
echo "========================================"

# Configuration
BASE_URL="http://localhost:8080/api"
ZIP_FILE="test-documents.zip"

# Check if ZIP file exists
if [ ! -f "$ZIP_FILE" ]; then
    echo "❌ Error: $ZIP_FILE not found!"
    echo "Please make sure the ZIP file is in the same directory as this script."
    exit 1
fi

echo "📁 Using ZIP file: $ZIP_FILE"
echo "🌐 API Base URL: $BASE_URL"
echo ""

# Step 1: Check if service is running
echo "1️⃣ Checking service health..."
HEALTH_RESPONSE=$(curl -s "$BASE_URL/health" 2>/dev/null)
if [ $? -eq 0 ]; then
    echo "✅ Service is running"
    echo "   Response: $HEALTH_RESPONSE"
else
    echo "❌ Service is not running or not accessible"
    echo "   Please start the service with: mvn spring-boot:run"
    exit 1
fi
echo ""

# Step 2: Upload ZIP file
echo "2️⃣ Uploading ZIP file..."
UPLOAD_RESPONSE=$(curl -s -X POST "$BASE_URL/upload" -F "file=@$ZIP_FILE")
if [ $? -eq 0 ]; then
    echo "✅ Upload successful"
    echo "   Response: $UPLOAD_RESPONSE"
    
    # Extract job ID from response (assuming JSON format)
    JOB_ID=$(echo "$UPLOAD_RESPONSE" | grep -o '"jobId":"[^"]*' | cut -d'"' -f4)
    if [ -n "$JOB_ID" ]; then
        echo "   Job ID: $JOB_ID"
    else
        echo "❌ Could not extract job ID from response"
        exit 1
    fi
else
    echo "❌ Upload failed"
    exit 1
fi
echo ""

# Step 3: Check processing status
echo "3️⃣ Checking processing status..."
MAX_ATTEMPTS=30
ATTEMPT=1

while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    echo "   Attempt $ATTEMPT/$MAX_ATTEMPTS - Checking status..."
    
    STATUS_RESPONSE=$(curl -s "$BASE_URL/status/$JOB_ID")
    if [ $? -eq 0 ]; then
        echo "   Response: $STATUS_RESPONSE"
        
        # Check if processing is complete
        if echo "$STATUS_RESPONSE" | grep -q '"status":"COMPLETED"'; then
            echo "✅ Processing completed successfully!"
            break
        elif echo "$STATUS_RESPONSE" | grep -q '"status":"FAILED"'; then
            echo "❌ Processing failed!"
            exit 1
        else
            echo "   Status: Processing... (waiting 2 seconds)"
            sleep 2
        fi
    else
        echo "❌ Failed to check status"
        exit 1
    fi
    
    ATTEMPT=$((ATTEMPT + 1))
done

if [ $ATTEMPT -gt $MAX_ATTEMPTS ]; then
    echo "❌ Processing timeout - took longer than expected"
    exit 1
fi
echo ""

# Step 4: Retrieve results
echo "4️⃣ Retrieving extraction results..."
RESULTS_RESPONSE=$(curl -s "$BASE_URL/results/$JOB_ID")
if [ $? -eq 0 ]; then
    echo "✅ Results retrieved successfully"
    echo "   Saving results to: extraction-results.json"
    echo "$RESULTS_RESPONSE" | python3 -m json.tool > extraction-results.json 2>/dev/null || echo "$RESULTS_RESPONSE" > extraction-results.json
    
    # Display summary
    echo ""
    echo "📊 Extraction Summary:"
    echo "   Job ID: $JOB_ID"
    echo "   Results saved to: extraction-results.json"
    
    # Try to extract some basic stats
    if command -v python3 >/dev/null 2>&1; then
        python3 -c "
import json, sys
try:
    with open('extraction-results.json', 'r') as f:
        data = json.load(f)
    print(f'   Total files processed: {data.get(\"totalFilesInZip\", \"N/A\")}')
    print(f'   Successful extractions: {data.get(\"successfulExtractions\", \"N/A\")}')
    print(f'   Processing time: {data.get(\"processingDuration\", \"N/A\")}')
    print(f'   Documents extracted: {len(data.get(\"extractedDocuments\", []))}')
except:
    pass
"
    fi
else
    echo "❌ Failed to retrieve results"
    exit 1
fi
echo ""

# Step 5: Show system info
echo "5️⃣ System Information:"
INFO_RESPONSE=$(curl -s "$BASE_URL/info")
if [ $? -eq 0 ]; then
    echo "$INFO_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$INFO_RESPONSE"
else
    echo "❌ Failed to retrieve system info"
fi
echo ""

echo "🎉 Test completed successfully!"
echo "📄 Check extraction-results.json for detailed results"
echo ""
echo "💡 You can also:"
echo "   - View all jobs: curl $BASE_URL/jobs"
echo "   - Delete this job: curl -X DELETE $BASE_URL/jobs/$JOB_ID"
echo "   - Check health: curl $BASE_URL/health"
