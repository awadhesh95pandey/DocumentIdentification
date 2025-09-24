# Document Extraction Service

A Spring Boot application that provides REST APIs for uploading ZIP files and extracting content from various document formats using Apache Tika.

## Features

- 📁 **ZIP File Upload**: Upload ZIP files containing documents
- 🔍 **Document Extraction**: Extract text content and metadata from various document formats
- 🔒 **Security**: Built-in protection against ZIP bombs and path traversal attacks
- ⚡ **Async Processing**: Non-blocking file processing with job status tracking
- 📊 **Rich Metadata**: Extract document properties, page counts, and more
- 🌐 **REST API**: Clean RESTful endpoints for easy integration

## Supported Document Formats

- **PDF**: `.pdf`
- **Microsoft Office**: `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`
- **OpenDocument**: `.odt`, `.ods`, `.odp`
- **Text Files**: `.txt`, `.rtf`
- And many more formats supported by Apache Tika

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.6 or higher

### Running the Application

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd DocumentIdentification
   ```

2. **Build the project**
   ```bash
   mvn clean compile
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

4. **Access the application**
   - Base URL: `http://localhost:8080/api`
   - Health Check: `http://localhost:8080/api/health`
   - System Info: `http://localhost:8080/api/info`

## API Endpoints

### Upload ZIP File
```http
POST /api/upload
Content-Type: multipart/form-data

Parameters:
- file: ZIP file to upload (max 100MB)
```

**Response:**
```json
{
  "jobId": "uuid-string",
  "fileName": "documents.zip",
  "fileSize": 1024000,
  "status": "UPLOADED",
  "message": "File uploaded successfully. Processing started.",
  "uploadTime": "2024-01-01 12:00:00"
}
```

### Check Job Status
```http
GET /api/status/{jobId}
```

**Response:**
```json
{
  "jobId": "uuid-string",
  "fileName": "documents.zip",
  "status": "COMPLETED",
  "message": "Processing completed successfully",
  "extractionResult": {
    "totalFilesInZip": 5,
    "successfulExtractions": 4,
    "failedExtractions": 1,
    "processingTimeMs": 2500
  }
}
```

### Get Extraction Results
```http
GET /api/results/{jobId}
```

**Response:**
```json
{
  "jobId": "uuid-string",
  "zipFileName": "documents.zip",
  "totalFilesInZip": 5,
  "successfulExtractions": 4,
  "extractedDocuments": [
    {
      "fileName": "document.pdf",
      "mimeType": "application/pdf",
      "fileSize": 204800,
      "content": "Extracted text content...",
      "metadata": {
        "title": "Document Title",
        "author": "Author Name",
        "created": "2024-01-01"
      },
      "pageCount": 10,
      "hasImages": true,
      "hasLinks": false
    }
  ],
  "processingDuration": "2.5 seconds"
}
```

### List All Jobs
```http
GET /api/jobs
```

### Delete Job
```http
DELETE /api/jobs/{jobId}
```

### System Information
```http
GET /api/info
```

### Health Check
```http
GET /api/health
```

## Configuration

The application can be configured through `application.properties`:

```properties
# Server Configuration
server.port=8080
server.servlet.context-path=/api

# File Upload Configuration
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=100MB

# Application Configuration
app.upload.dir=./uploads
app.temp.dir=./temp
app.max-zip-entries=1000
app.max-zip-size=100MB
app.allowed-file-types=zip,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,rtf,odt,ods,odp

# Security Configuration
app.security.scan-files=true
app.security.max-filename-length=255
```

## Usage Examples

### Using cURL

**Upload a ZIP file:**
```bash
curl -X POST \
  http://localhost:8080/api/upload \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@documents.zip'
```

**Check status:**
```bash
curl http://localhost:8080/api/status/{jobId}
```

**Get results:**
```bash
curl http://localhost:8080/api/results/{jobId}
```

### Using JavaScript/Fetch

```javascript
// Upload file
const formData = new FormData();
formData.append('file', zipFile);

const response = await fetch('http://localhost:8080/api/upload', {
  method: 'POST',
  body: formData
});

const result = await response.json();
console.log('Job ID:', result.jobId);

// Check status
const statusResponse = await fetch(`http://localhost:8080/api/status/${result.jobId}`);
const status = await statusResponse.json();
console.log('Status:', status.status);
```

## Security Features

- **ZIP Bomb Protection**: Limits on file size, entry count, and extraction size
- **Path Traversal Prevention**: Sanitizes file paths to prevent directory traversal attacks
- **File Type Validation**: Only allows specified file types for processing
- **Size Limits**: Configurable limits on file sizes and ZIP contents
- **Input Validation**: Comprehensive validation of all inputs

## Development

### Project Structure
```
src/
├── main/
│   ├── java/org/example/
│   │   ├── controller/          # REST controllers
│   │   ├── service/             # Business logic services
│   │   ├── model/               # Data models
│   │   ├── config/              # Configuration classes
│   │   └── util/                # Utility classes
│   └── resources/
│       └── application.properties
└── test/
    └── java/org/example/        # Test classes
```

### Building for Production

```bash
# Build JAR file
mvn clean package

# Run the JAR
java -jar target/PDLDocumentIdentification-1.0-SNAPSHOT.jar
```

### Docker Support

Create a `Dockerfile`:
```dockerfile
FROM openjdk:21-jre-slim
COPY target/PDLDocumentIdentification-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

Build and run:
```bash
docker build -t document-extraction-service .
docker run -p 8080:8080 document-extraction-service
```

## Testing

Run tests:
```bash
mvn test
```

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**: Increase JVM heap size with `-Xmx2g`
2. **File too large**: Check `spring.servlet.multipart.max-file-size` setting
3. **Unsupported file type**: Verify file extension is in `app.allowed-file-types`
4. **Processing timeout**: Large files may take time; check job status periodically

### Logs

Enable debug logging:
```properties
logging.level.org.example=DEBUG
logging.level.org.apache.tika=INFO
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Support

For issues and questions, please create an issue in the repository.
