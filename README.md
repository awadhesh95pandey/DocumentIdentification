# Document Classifier API - Java Spring Boot

A Java Spring Boot REST API for classifying Indian identity documents using Google Cloud Vision API for OCR and Google Vertex AI Gemini for document classification.

## 🚀 Features

- **ZIP File Processing**: Upload ZIP files containing multiple document images
- **OCR Text Extraction**: Uses Google Cloud Vision API for accurate text extraction
- **AI-Powered Classification**: Leverages Google Vertex AI Gemini 2.0-flash model for document classification
- **Supported Document Types**:
  - Aadhaar Card
  - PAN Card
  - Voter ID
  - Driving License
- **Robust Error Handling**: Comprehensive error handling and validation
- **Temporary File Management**: Automatic cleanup of temporary files

## 🛠️ Technology Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Google Cloud Vision API** - OCR text extraction
- **Google Cloud Vertex AI** - Document classification
- **Maven** - Dependency management
- **SLF4J + Logback** - Logging

## 📋 Prerequisites

1. **Java 17** or higher
2. **Maven 3.6+**
3. **Google Cloud Project** with the following APIs enabled:
   - Cloud Vision API
   - Vertex AI API
4. **Google Cloud Authentication** (Service Account or Application Default Credentials)

## ⚙️ Setup Instructions

### 1. Clone the Repository
```bash
git clone <repository-url>
cd document-classifier
```

### 2. Google Cloud Setup

#### Enable Required APIs
```bash
gcloud services enable vision.googleapis.com
gcloud services enable aiplatform.googleapis.com
```

#### Set up Authentication
Option A: Service Account (Recommended for production)
```bash
# Create service account
gcloud iam service-accounts create document-classifier-sa

# Grant necessary permissions
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:document-classifier-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/aiplatform.user"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:document-classifier-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/vision.imageAnnotator"

# Create and download key
gcloud iam service-accounts keys create key.json \
    --iam-account=document-classifier-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com

# Set environment variable
export GOOGLE_APPLICATION_CREDENTIALS="path/to/key.json"
```

Option B: Application Default Credentials (For development)
```bash
gcloud auth application-default login
```

### 3. Configure Environment Variables

Create a `.env` file or set environment variables:
```bash
export GOOGLE_CLOUD_PROJECT=your-project-id
export GOOGLE_CLOUD_LOCATION=us-central1
export GEMINI_MODEL=gemini-2.0-flash
```

### 4. Build and Run

```bash
# Build the application
mvn clean compile

# Run the application
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

## 📚 API Documentation

### Classify Documents
**Endpoint**: `POST /api/classify-documents`

**Description**: Upload a ZIP file containing document images for classification.

**Request**:
- Method: `POST`
- Content-Type: `multipart/form-data`
- Parameter: `file` (ZIP file containing images)

**Response**:
```json
{
  "image1.jpg": "PAN",
  "image2.png": "Aadhaar",
  "image3.webp": "Voter ID"
}
```

**Example using cURL**:
```bash
curl -X POST \
  http://localhost:8080/api/classify-documents \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@documents.zip'
```

### Health Check
**Endpoint**: `GET /api/health`

**Response**:
```json
{
  "status": "healthy",
  "service": "Document Classifier API",
  "version": "1.0.0"
}
```

## 🔧 Configuration

### Application Properties
The application can be configured via `application.yml`:

```yaml
server:
  port: 8080

spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

google:
  cloud:
    project-id: ${GOOGLE_CLOUD_PROJECT:your-project-id}
    location: ${GOOGLE_CLOUD_LOCATION:us-central1}

gemini:
  model: ${GEMINI_MODEL:gemini-2.0-flash}
```

### Supported Image Formats
- PNG (.png)
- JPEG (.jpg, .jpeg)
- WebP (.webp)
- BMP (.bmp)

## 🚀 Deployment

### Docker Deployment
```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app
COPY target/document-classifier-1.0.0.jar app.jar
COPY key.json key.json

ENV GOOGLE_APPLICATION_CREDENTIALS=/app/key.json

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
```

### Google Cloud Run
```bash
# Build and deploy to Cloud Run
gcloud run deploy document-classifier \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated
```

## 🧪 Testing

### Unit Tests
```bash
mvn test
```

### Integration Testing
```bash
# Test with sample ZIP file
curl -X POST \
  http://localhost:8080/api/classify-documents \
  -F 'file=@test-documents.zip'
```

## 📊 Monitoring and Logging

The application uses SLF4J with Logback for logging. Logs include:
- Request/response information
- Processing times
- Error details
- Classification results

## 🔒 Security Considerations

1. **File Size Limits**: Maximum 50MB per upload
2. **File Type Validation**: Only ZIP files accepted
3. **Temporary File Cleanup**: Automatic cleanup after processing
4. **Input Validation**: Comprehensive validation of inputs
5. **Error Handling**: Secure error messages without sensitive information

## 🤝 Migration from Python

This Java implementation provides equivalent functionality to the original Python FastAPI version:

| Python (FastAPI) | Java (Spring Boot) |
|------------------|-------------------|
| `FastAPI` | `@RestController` |
| `UploadFile` | `MultipartFile` |
| `tempfile.TemporaryDirectory` | `Files.createTempDirectory()` |
| `zipfile.ZipFile` | `ZipInputStream` |
| `google.cloud.vision` | `ImageAnnotatorClient` |
| `vertexai.GenerativeModel` | `PredictionServiceClient` |

## 📝 License

This project is licensed under the MIT License.

## 🆘 Troubleshooting

### Common Issues

1. **Authentication Error**
   ```
   Solution: Ensure GOOGLE_APPLICATION_CREDENTIALS is set correctly
   ```

2. **API Not Enabled**
   ```
   Solution: Enable Vision API and Vertex AI API in Google Cloud Console
   ```

3. **File Size Error**
   ```
   Solution: Check file size limits in application.yml
   ```

4. **Memory Issues**
   ```
   Solution: Increase JVM heap size: -Xmx2g
   ```

For more help, check the logs or create an issue in the repository.
