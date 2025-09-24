# Test Files for Document Extraction Service

This directory contains test files to help you test the Document Extraction Service.

## 📁 Files Included

### Test Documents (Individual Files)
- **`sample-document.txt`** - Basic text document with various content types
- **`business-report.txt`** - Sample quarterly business report with financial data
- **`technical-specification.txt`** - Technical documentation with system specifications
- **`meeting-notes.txt`** - Meeting notes with attendees, agenda, and action items
- **`user-manual.txt`** - User guide with instructions and FAQ

### Test Package
- **`test-documents.zip`** - ZIP file containing all 5 test documents (6.9 KB)

### Test Script
- **`test-api.sh`** - Automated test script to demonstrate API usage

## 🚀 How to Use

### Option 1: Use the Test Script (Recommended)
1. Start the Document Extraction Service:
   ```bash
   mvn spring-boot:run
   ```

2. In another terminal, navigate to the test-files directory:
   ```bash
   cd test-files
   ```

3. Run the automated test script:
   ```bash
   ./test-api.sh
   ```

The script will:
- Check if the service is running
- Upload the test ZIP file
- Monitor processing status
- Retrieve and save results to `extraction-results.json`
- Display a summary of the extraction

### Option 2: Manual Testing with cURL

1. **Upload the ZIP file:**
   ```bash
   curl -X POST http://localhost:8080/api/upload \
     -F "file=@test-documents.zip"
   ```

2. **Check processing status** (replace `{jobId}` with the actual job ID):
   ```bash
   curl http://localhost:8080/api/status/{jobId}
   ```

3. **Get extraction results:**
   ```bash
   curl http://localhost:8080/api/results/{jobId}
   ```

### Option 3: Using Postman or Similar Tools

1. **POST** to `http://localhost:8080/api/upload`
   - Set Content-Type to `multipart/form-data`
   - Add form field `file` with `test-documents.zip`

2. **GET** `http://localhost:8080/api/status/{jobId}`

3. **GET** `http://localhost:8080/api/results/{jobId}`

## 📊 Expected Results

The test ZIP file contains 5 text documents with different types of content:

- **Total files in ZIP:** 5
- **Expected successful extractions:** 5
- **Total content size:** ~13.5 KB
- **Processing time:** Usually under 10 seconds

Each document should be successfully processed with:
- Full text content extracted
- Basic metadata (filename, size, MIME type)
- File information (character count, etc.)

## 🔍 What to Look For

When testing, verify that:

1. **Upload Response** includes:
   - Unique job ID (UUID format)
   - File name: "test-documents.zip"
   - File size: ~6943 bytes
   - Status: "UPLOADED"

2. **Status Response** shows progression:
   - Initial: "UPLOADED" or "PROCESSING"
   - Final: "COMPLETED"

3. **Results Response** contains:
   - Array of 5 extracted documents
   - Text content from each file
   - Metadata for each document
   - Processing statistics

## 🛠 Troubleshooting

**Service not responding?**
- Make sure the service is running: `mvn spring-boot:run`
- Check the service is accessible: `curl http://localhost:8080/api/health`

**Upload fails?**
- Verify the ZIP file exists and is not corrupted
- Check file size is under 100MB limit

**Processing takes too long?**
- The test files should process quickly (under 30 seconds)
- Check service logs for any errors

**No content extracted?**
- Verify the ZIP file contains valid text documents
- Check that files are not corrupted or password-protected

## 📝 Creating Your Own Test Files

To create additional test files:

1. Create your documents (PDF, Word, Excel, PowerPoint, text files)
2. Create a ZIP file containing them:
   ```bash
   zip my-test-documents.zip *.pdf *.docx *.txt
   ```
3. Upload using the same API endpoints

## 🔗 API Reference

- **Health Check:** `GET /api/health`
- **System Info:** `GET /api/info`
- **Upload File:** `POST /api/upload`
- **Check Status:** `GET /api/status/{jobId}`
- **Get Results:** `GET /api/results/{jobId}`
- **List Jobs:** `GET /api/jobs`
- **Delete Job:** `DELETE /api/jobs/{jobId}`

## 📄 Sample Output

After running the test, you should see output similar to:
```json
{
  "jobId": "12345678-1234-1234-1234-123456789012",
  "zipFileName": "test-documents.zip",
  "totalFilesInZip": 5,
  "successfulExtractions": 5,
  "extractedDocuments": [
    {
      "fileName": "sample-document.txt",
      "mimeType": "text/plain",
      "fileSize": 1231,
      "content": "Sample Text Document for Testing...",
      "metadata": {...}
    }
    // ... 4 more documents
  ],
  "processingDuration": "2.5 seconds"
}
```

Happy testing! 🎉
