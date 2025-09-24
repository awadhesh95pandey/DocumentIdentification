# Document Extraction Service with AI Classification

A Spring Boot application that provides REST APIs for uploading ZIP files, extracting content from various document formats using Apache Tika, and classifying documents using OpenAI's GPT models.

## Features

- 📁 **ZIP File Upload**: Upload ZIP files containing documents and images
- 🔍 **Document Extraction**: Extract text content and metadata from various document formats
- 🤖 **AI Classification**: Intelligent document type identification using OpenAI GPT models
- 🖼️ **Image Support**: Process and classify image documents (JPG, PNG, PDF, etc.)
- 🇮🇳 **Indian Document Types**: Specialized classification for Aadhar, PAN, Passport, Driving License, etc.
- 🔒 **Security**: Built-in protection against ZIP bombs and path traversal attacks
- ⚡ **Async Processing**: Non-blocking file processing with job status tracking
- 📊 **Rich Metadata**: Extract document properties, page counts, and classification results
- 🌐 **REST API**: Clean RESTful endpoints for easy integration

## Supported Document Formats

### Text Documents
- **PDF**: `.pdf`
- **Microsoft Office**: `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`
- **OpenDocument**: `.odt`, `.ods`, `.odp`
- **Text Files**: `.txt`, `.rtf`
- And many more formats supported by Apache Tika

### Image Documents
- **JPEG**: `.jpg`, `.jpeg`
- **PNG**: `.png`
- **GIF**: `.gif`
- **BMP**: `.bmp`
- **TIFF**: `.tiff`, `.tif`
- **WebP**: `.webp`

## Document Classification Types

The AI classification system can identify the following document types:

### Indian Identity Documents
- **Aadhar Card**: Indian national identity card with 12-digit UID
- **PAN Card**: Permanent Account Number for tax identification
- **Passport**: International travel document
- **Driving License**: Motor vehicle operation permit
- **Voter ID**: Electoral identity card
- **Ration Card**: Subsidized food grain access document

### Financial Documents
- **Bank Statement**: Account transaction history
- **Salary Slip**: Employee salary and deduction details
- **Utility Bill**: Electricity, water, gas, telecom bills
- **Invoice**: Commercial bills for goods/services
- **Receipt**: Payment proof or transaction receipt

### Official Documents
- **Property Document**: Property ownership/rental documents
- **Insurance Document**: Policy or claim documents
- **Medical Report**: Healthcare/medical examination documents
- **Educational Certificate**: Academic degrees, diplomas, certificates
- **Employment Letter**: Job offers, experience letters
- **Business Registration**: Company incorporation, business licenses
- **Tax Document**: Income tax returns, tax certificates
- **Legal Document**: Court orders, legal notices, contracts

### General Documents
- **Form**: Application forms or official forms
- **Letter**: Formal or informal correspondence
- **Report**: Business, technical, or analytical reports
- **Presentation**: Slide decks or presentation materials
- **Spreadsheet**: Data tables or calculation documents
- **Other**: Document type not specifically classified
- **Unknown**: Unable to determine document type

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- OpenAI API Key (for document classification features)

### Running the Application

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd DocumentIdentification
   ```

2. **Configure OpenAI API Key** (Optional but recommended)
   ```bash
   export OPENAI_API_KEY=your-openai-api-key-here
   ```
   
   Or set it in `application.properties`:
   ```properties
   openai.api.key=your-openai-api-key-here
   ```

3. **Build the project**
   ```bash
   mvn clean compile
   ```

4. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

5. **Access the application**
   - Base URL: `http://localhost:8080/api`
   - Health Check: `http://localhost:8080/api/health`
   - System Info: `http://localhost:8080/api/info`

## Configuration

### OpenAI Settings

Configure OpenAI integration in `application.properties`:

```properties
# OpenAI Configuration
openai.api.key=${OPENAI_API_KEY:your-openai-api-key-here}
openai.api.model=gpt-4-vision-preview
openai.api.timeout=60
openai.api.max-tokens=1000
openai.classification.enabled=true

# Image Processing Configuration
app.image.max-size=10MB
app.image.allowed-formats=jpg,jpeg,png,pdf,tiff,bmp
app.image.resize-max-width=1024
app.image.resize-max-height=1024
```

### Environment Variables

- `OPENAI_API_KEY`: Your OpenAI API key for document classification
- Set this environment variable to enable AI-powered document classification

### Classification Behavior

- **With OpenAI API Key**: Full AI-powered classification with high accuracy
- **Without OpenAI API Key**: Fallback to filename-based heuristic classification
- **Image Documents**: Processed using GPT-4 Vision for visual document analysis
- **Text Documents**: Processed using GPT-4 for content-based classification

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
      "fileName": "aadhar-card.jpg",
      "mimeType": "image/jpeg",
      "fileSize": 204800,
      "content": "Extracted text content...",
      "metadata": {
        "title": "Aadhar Card Image",
        "format": "JPEG",
        "dimensions": "1024x768"
      },
      "pageCount": 1,
      "hasImages": true,
      "hasLinks": false,
      "classification": {
        "documentType": "AADHAR_CARD",
        "confidence": 0.95,
        "reasoning": "Document contains Aadhar card layout with UID number format and government logo",
        "aiModel": "gpt-4-vision-preview",
        "isImageBased": true,
        "classifiedAt": "2024-01-01 10:30:45",
        "processingTimeMs": 2500
      }
    },
    {
      "fileName": "bank-statement.pdf",
      "mimeType": "application/pdf",
      "fileSize": 156789,
      "content": "Bank statement content with transactions...",
      "metadata": {
        "title": "Monthly Bank Statement",
        "author": "Bank Name",
        "created": "2024-01-01"
      },
      "pageCount": 3,
      "hasImages": false,
      "hasLinks": true,
      "classification": {
        "documentType": "BANK_STATEMENT",
        "confidence": 0.92,
        "reasoning": "Document contains transaction history, account numbers, and bank letterhead",
        "aiModel": "gpt-4",
        "isImageBased": false,
        "classifiedAt": "2024-01-01 10:30:47",
        "processingTimeMs": 1800
      }
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
