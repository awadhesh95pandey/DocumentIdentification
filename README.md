# Secure Document Classifier API with VaultGemma - Java Spring Boot

A Java Spring Boot REST API for classifying Indian identity documents using Google Cloud Vision API for OCR and **Google's VaultGemma** for privacy-preserving document classification with differential privacy guarantees.

## 🚀 Features

### 🔒 **VaultGemma Privacy Protection**
- **Differential Privacy**: Mathematical privacy guarantees using Google's VaultGemma model
- **Privacy Budget Management**: Per-user epsilon budget tracking and enforcement
- **Secure Document Vault**: AES-256 encrypted storage with integrity verification
- **Audit Logging**: Comprehensive privacy and security event tracking
- **Zero Data Leakage**: In-memory processing with secure cleanup

### 📄 **Document Processing**
- **ZIP File Processing**: Upload ZIP files containing multiple document images
- **OCR Text Extraction**: Uses Google Cloud Vision API for accurate text extraction
- **Privacy-Preserving Classification**: VaultGemma with differential privacy for document classification
- **Supported Document Types**:
  - Aadhaar Card
  - PAN Card
  - Voter ID
  - Driving License

### 🛡️ **Document Security**
- **Document Encryption**: AES-256 encrypted storage for all documents
- **Secure Processing**: In-memory processing with automatic cleanup
- **Privacy Protection**: VaultGemma differential privacy for classification
- **Audit Logging**: Document processing events tracked for compliance

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

### Document Classification with VaultGemma
**Endpoint**: `POST /api/classify-documents`

**Description**: Upload a ZIP file containing document images for classification. Automatically uses VaultGemma differential privacy when enabled.

**Request**:
- Method: `POST`
- Content-Type: `multipart/form-data`
- Parameter: `file` (ZIP file containing images)

**Response**:
```json
{
  "results": {
    "image1.jpg": {
      "classification": "PAN",
      "documentId": "DOC_ABC123",
      "securelyStored": true,
      "privacyProtected": true
    },
    "image2.png": {
      "classification": "Aadhaar", 
      "documentId": "DOC_DEF456",
      "securelyStored": true,
      "privacyProtected": true
    }
  },
  "privacyBudgetStatus": {
    "usedBudget": 0.2,
    "remainingBudget": 0.8,
    "budgetExceeded": false
  },
  "vaultGemmaEnabled": true,
  "differentialPrivacy": true
}
```

**Example using cURL**:
```bash
curl -X POST \
  http://localhost:8080/api/classify-documents \
  -H 'Content-Type: multipart/form-data' \
  -F 'file=@documents.zip'
```

### Document Vault Management

**List Documents**: `GET /api/documents`
```json
{
  "documents": [
    {
      "documentId": "DOC_ABC123",
      "originalFilename": "aadhaar.jpg",
      "documentType": "Aadhaar",
      "storedTimestamp": "2024-01-01T12:00:00",
      "fileSize": 1024,
      "encrypted": true
    }
  ],
  "totalCount": 1
}
```

**Retrieve Document**: `GET /api/documents/{documentId}`

**Delete Document**: `DELETE /api/documents/{documentId}`

### Privacy Budget Management

**Check Budget**: `GET /api/privacy-budget`
```json
{
  "userId": "user",
  "usedBudget": 0.3,
  "maxBudget": 1.0,
  "remainingBudget": 0.7,
  "budgetExceeded": false
}
```

**Reset Budget**: `POST /api/privacy-budget/reset`

### Health Check
**Endpoint**: `GET /api/health`

**Response**:
```json
{
  "status": "healthy",
  "service": "Document Classifier API with VaultGemma",
  "version": "2.0.0",
  "vaultGemmaEnabled": true,
  "differentialPrivacy": true,
  "encryptionEnabled": true,
  "auditingEnabled": true,
  "vaultGemmaModelAvailable": true
}
```

## 🏗️ Architecture

### Unified Controller Design
- **Single Controller**: `DocumentClassifierController` handles all document operations
- **VaultGemma Integration**: Separate `VaultGemmaIntegration` service for clean separation of concerns
- **Automatic Fallback**: Uses VaultGemma when available, falls back to regular classification
- **No Duplicate Code**: Eliminated redundant controllers and consolidated functionality

### VaultGemma Integration
- **Privacy-First**: Differential privacy applied automatically when VaultGemma is enabled
- **Secure Storage**: All documents encrypted with AES-256 and stored in secure vault
- **Budget Management**: Privacy budget tracking prevents excessive data usage
- **Audit Trail**: Complete logging of all document processing activities

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
