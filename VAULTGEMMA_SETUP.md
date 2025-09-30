# VaultGemma Hugging Face Integration Setup Guide

## 🌐 Online VaultGemma with Hugging Face API

This guide shows you how to set up VaultGemma to work with Hugging Face's online API instead of downloading the model locally.

## 📋 Prerequisites

### 1. Hugging Face Account Setup
1. **Create Account**: Go to https://huggingface.co/join
2. **Get API Token**: 
   - Visit: https://huggingface.co/settings/tokens
   - Click "New token" → "Read" access
   - Copy your token (starts with `hf_...`)
3. **Accept VaultGemma License**:
   - Visit: https://huggingface.co/google/vaultgemma-1b
   - Click "Accept License" (required for API access)

## 🔧 Configuration

### 1. Environment Variables
Set your Hugging Face token as an environment variable:

**Windows:**
```cmd
set HUGGINGFACE_TOKEN=hf_your_token_here
```

**Linux/Mac:**
```bash
export HUGGINGFACE_TOKEN=hf_your_token_here
```

**Or add to your IDE/application.yml:**
```yaml
vaultgemma:
  huggingface:
    token: hf_your_actual_token_here
```

### 2. Application Configuration
The following configuration is already added to `application.yml`:

```yaml
vaultgemma:
  enabled: true
  huggingface:
    api-url: https://api-inference.huggingface.co/models/google/vaultgemma-1b
    token: ${HUGGINGFACE_TOKEN:your_token_here}
    timeout: 30000
  model:
    privacy-budget: 1.0
    epsilon-per-query: 0.1
```

## 🚀 Features Added

### 1. New Service: `HuggingFaceVaultGemmaService`
- **Location**: `src/main/java/com/documentclassifier/service/HuggingFaceVaultGemmaService.java`
- **Features**:
  - Direct API calls to Hugging Face VaultGemma
  - Privacy-preserving document classification
  - Automatic fallback to rule-based classification
  - Error handling and retry logic
  - Service availability checking

### 2. Updated Dependencies
Added to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 3. Enhanced VaultGemmaService
- **Automatic API Selection**: Tries Hugging Face API first, falls back to local patterns
- **Improved Error Handling**: Multiple fallback layers
- **Better Logging**: Detailed status information

### 4. New Status Endpoint
**Endpoint**: `GET /api/vaultgemma/status`

**Response**:
```json
{
    "vaultGemmaEnabled": true,
    "modelAvailable": true,
    "huggingFaceService": {
        "service": "HuggingFace VaultGemma",
        "available": true,
        "apiUrl": "https://api-inference.huggingface.co/models/google/vaultgemma-1b",
        "hasToken": true,
        "timeout": 30000
    },
    "serviceAvailable": true,
    "privacyBudget": 1.0,
    "epsilonPerQuery": 0.1,
    "differentialPrivacy": true
}
```

## 🧪 Testing

### 1. Check Service Status
```bash
curl http://localhost:8080/api/vaultgemma/status
```

### 2. Test Document Classification
Upload documents via the existing endpoints:
```bash
curl -X POST -F "files=@document.jpg" http://localhost:8080/api/classify
```

### 3. Expected Response
With VaultGemma online integration:
```json
{
    "totalProcessed": 1,
    "vaultGemmaEnabled": true,
    "results": {
        "document.jpg": {
            "classification": "PAN",
            "vaultGemmaUsed": true,
            "securelyStored": true,
            "privacyProtected": true,
            "documentId": "vault_doc_12345"
        }
    },
    "differentialPrivacy": true,
    "privacyBudgetStatus": {
        "usedBudget": 0.1,
        "remainingBudget": 0.9
    }
}
```

## 🔄 How It Works

### 1. Classification Flow
```
📤 Document Upload
    ↓
☁️ GCP Vertex AI (OCR) → Extract Text
    ↓
🤖 Hugging Face VaultGemma API → Classify with Privacy
    ↓
🛡️ Differential Privacy Applied
    ↓
💾 Secure Vault Storage
    ↓
📊 Response with Privacy Metrics
```

### 2. Fallback Strategy
1. **Primary**: Hugging Face VaultGemma API
2. **Fallback 1**: Local pattern-based classification with differential privacy
3. **Fallback 2**: Basic rule-based classification

## 🛡️ Privacy Features

### 1. Differential Privacy
- **Privacy Budget**: 1.0 epsilon total per user
- **Per Query**: 0.1 epsilon consumed per classification
- **Noise Addition**: Calibrated noise added to protect individual data points

### 2. Secure Processing
- **Text Limiting**: Input text limited to 500 characters for privacy
- **Encryption**: All stored documents encrypted with AES-256-GCM
- **Audit Logging**: All operations logged for compliance
- **Memory Clearing**: Sensitive data cleared from memory after processing

## 🚨 Troubleshooting

### 1. API Token Issues
- **Error**: "Unauthorized" or "Invalid token"
- **Solution**: Verify your token is correct and has been set as environment variable

### 2. License Not Accepted
- **Error**: "Access denied" or "Gated model"
- **Solution**: Visit https://huggingface.co/google/vaultgemma-1b and accept the license

### 3. API Rate Limits
- **Error**: "Rate limit exceeded"
- **Solution**: The service automatically falls back to local classification

### 4. Network Issues
- **Error**: "Connection timeout"
- **Solution**: Check internet connectivity; service will use fallback classification

## 📊 Benefits of Online Integration

### ✅ Advantages
- **No Local Storage**: No need to download 2-4 GB model files
- **Always Updated**: Access to latest model version
- **Scalable**: No local compute requirements
- **Easy Setup**: Just need API token

### ⚠️ Considerations
- **Internet Required**: Needs internet connectivity for API calls
- **API Costs**: Hugging Face may have usage limits (currently free for inference)
- **Latency**: Network calls add ~1-2 seconds per classification

## 🎯 Next Steps

1. **Set your Hugging Face token**
2. **Restart your application**
3. **Test the `/api/vaultgemma/status` endpoint**
4. **Upload documents to see VaultGemma in action**

Your VaultGemma integration is now ready to use with online Hugging Face API! 🚀
