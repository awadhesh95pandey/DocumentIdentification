# 🔒 Local VaultGemma-1B Model Setup Guide

This guide will help you configure your Document Classification application to use the **Google VaultGemma-1B model locally** instead of the Hugging Face API.

## 📋 Prerequisites

### 1. Downloaded VaultGemma-1B Model
- You should have already downloaded the Google VaultGemma-1B model files
- The model files should include:
  - `model.onnx` (ONNX format model file)
  - `tokenizer.json` (Hugging Face tokenizer configuration)
  - `config.json` (model configuration)
  - Other supporting files

### 2. System Requirements
- **Java 17+** (already configured in your project)
- **Memory**: At least 8GB RAM (16GB recommended)
- **Storage**: ~4GB free space for model files
- **CPU**: Multi-core processor (4+ cores recommended)

## 🗂️ Directory Structure Setup

### Step 1: Create Model Directory
Your application expects the model files in the following structure:

```
DocumentIdentification/
├── models/
│   └── vaultgemma-1b/
│       ├── model.onnx          # Main ONNX model file
│       ├── tokenizer.json      # Tokenizer configuration
│       ├── config.json         # Model configuration
│       └── vocab.txt           # Vocabulary file (if available)
├── src/
├── pom.xml
└── ...
```

### Step 2: Move Your Downloaded Model Files

1. **Create the directory structure:**
   ```bash
   mkdir -p models/vaultgemma-1b
   ```

2. **Copy your downloaded VaultGemma-1B files:**
   ```bash
   # Replace /path/to/your/downloaded/model with your actual download location
   cp /path/to/your/downloaded/model/* models/vaultgemma-1b/
   ```

3. **Verify the files are in place:**
   ```bash
   ls -la models/vaultgemma-1b/
   ```

   You should see files like:
   ```
   -rw-r--r-- 1 user user 2.1G model.onnx
   -rw-r--r-- 1 user user  15K tokenizer.json
   -rw-r--r-- 1 user user  1.2K config.json
   ```

## ⚙️ Configuration

### Step 3: Environment Variables (Optional)
You can customize the local model configuration using environment variables:

```bash
# Model path (default: models/vaultgemma-1b)
export VAULTGEMMA_MODEL_PATH=models/vaultgemma-1b

# Enable local model (default: true)
export VAULTGEMMA_ENABLE_LOCAL=true

# Number of threads for inference (default: 4)
export VAULTGEMMA_THREADS=4

# Model loading timeout in seconds (default: 300)
export VAULTGEMMA_LOAD_TIMEOUT=300

# Maximum tokens per inference (default: 1024)
export VAULTGEMMA_MAX_TOKENS=1024

# Privacy settings
export VAULTGEMMA_PRIVACY_BUDGET=1.0
export VAULTGEMMA_EPSILON_PER_QUERY=0.1
```

### Step 4: Application Configuration
The application is already configured to use local models. The key settings in `application.yml`:

```yaml
vaultgemma:
  enabled: true
  model:
    path: models/vaultgemma-1b           # Local model path
    enable-local-model: true             # Enable local model
    threads: 4                           # CPU threads for inference
    max-tokens: 1024                     # Maximum tokens per request
    load-timeout-seconds: 300            # Model loading timeout
    privacy-budget: 1.0                  # Differential privacy budget
    epsilon-per-query: 0.1               # Privacy noise per query
```

## 🚀 Running the Application

### Step 5: Start the Application
```bash
# Using Maven
mvn spring-boot:run

# Or using Java directly (after building)
mvn clean package
java -jar target/document-classifier-1.0.0.jar
```

### Step 6: Verify Local Model Loading
Watch the application logs during startup. You should see:

```
INFO  - Initializing local VaultGemma model...
INFO  - Loading ONNX model from: /path/to/models/vaultgemma-1b/model.onnx
INFO  - ONNX model loaded with 2 input(s) and 1 output(s)
INFO  - Loading tokenizer from: /path/to/models/vaultgemma-1b/tokenizer.json
INFO  - Tokenizer loaded successfully
INFO  - ✅ Local VaultGemma model loaded successfully!
INFO  - ✅ Local VaultGemma model available at: /path/to/models/vaultgemma-1b
```

## 🧪 Testing the Local Model

### Step 7: Check Model Status
```bash
curl http://localhost:8080/api/vaultgemma/status
```

**Expected Response:**
```json
{
  "vaultGemmaEnabled": true,
  "modelAvailable": true,
  "primaryMethod": "local",
  "localModel": {
    "enabled": true,
    "available": true,
    "path": "models/vaultgemma-1b",
    "metrics": {
      "modelLoaded": true,
      "modelAvailable": true,
      "totalInferences": 0,
      "averageInferenceTime": 0
    }
  },
  "apiModel": {
    "available": false,
    "service": "Hugging Face VaultGemma"
  },
  "patternFallback": {
    "available": true,
    "description": "Privacy-preserving pattern-based classification"
  }
}
```

### Step 8: Test Document Classification
```bash
# Upload a test document
curl -X POST -F "files=@test-document.jpg" http://localhost:8080/api/classify
```

**Expected Response:**
```json
{
  "totalProcessed": 1,
  "vaultGemmaEnabled": true,
  "results": {
    "test-document.jpg": {
      "classification": "PAN",
      "vaultGemmaUsed": true,
      "localModelUsed": true,
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

## 🔄 Fallback Strategy

Your application now uses a **three-tier fallback system**:

1. **🥇 Local VaultGemma Model** (Primary)
   - Fastest inference
   - Complete privacy (no network calls)
   - Best accuracy

2. **🥈 Hugging Face API** (Secondary)
   - Used if local model fails
   - Requires internet connection
   - Good accuracy

3. **🥉 Pattern-Based Classification** (Final Fallback)
   - Always available
   - Rule-based classification
   - Privacy-preserving with differential privacy

## 🛠️ Troubleshooting

### Common Issues

#### 1. Model Files Not Found
**Error:** `Local VaultGemma model directory not found`

**Solution:**
- Verify the model files are in `models/vaultgemma-1b/`
- Check file permissions: `chmod -R 755 models/`
- Ensure `model.onnx` and `tokenizer.json` exist

#### 2. Out of Memory Error
**Error:** `OutOfMemoryError` during model loading

**Solutions:**
- Increase JVM heap size: `java -Xmx8g -jar app.jar`
- Reduce thread count: `VAULTGEMMA_THREADS=2`
- Close other applications to free memory

#### 3. Model Loading Timeout
**Error:** `Model loading timeout exceeded`

**Solutions:**
- Increase timeout: `VAULTGEMMA_LOAD_TIMEOUT=600`
- Check disk I/O performance
- Ensure model files are not corrupted

#### 4. ONNX Runtime Issues
**Error:** `OrtException` during inference

**Solutions:**
- Verify ONNX model format compatibility
- Check if model was exported correctly
- Try reducing `max-tokens` setting

### Performance Optimization

#### Memory Settings
```bash
# For production deployment
java -Xms4g -Xmx8g -XX:+UseG1GC -jar app.jar
```

#### Thread Configuration
```bash
# Adjust based on your CPU cores
export VAULTGEMMA_THREADS=8  # For 8+ core systems
export VAULTGEMMA_THREADS=4  # For 4-6 core systems
export VAULTGEMMA_THREADS=2  # For 2-4 core systems
```

## 📊 Monitoring and Metrics

### Health Check Endpoint
```bash
curl http://localhost:8080/api/vaultgemma/health
```

### Performance Metrics
The local model tracks:
- Total inferences performed
- Average inference time
- Last inference time
- Model loading status
- Memory usage

### Logs
Monitor application logs for:
- Model loading progress
- Inference performance
- Fallback usage
- Privacy events
- Error conditions

## 🔒 Privacy Features

### Differential Privacy
- **Privacy Budget**: 1.0 epsilon per user
- **Per Query Cost**: 0.1 epsilon
- **Noise Addition**: Calibrated noise for privacy protection

### Data Protection
- **Text Preprocessing**: PII sanitization before inference
- **Memory Clearing**: Sensitive data cleared after processing
- **Audit Logging**: All operations logged for compliance
- **Encryption**: Stored documents encrypted with AES-256-GCM

## 🎯 Next Steps

1. **✅ Verify model files are in correct location**
2. **✅ Start the application and check logs**
3. **✅ Test the status endpoint**
4. **✅ Upload test documents**
5. **✅ Monitor performance metrics**
6. **✅ Configure production settings**

## 📞 Support

If you encounter issues:

1. **Check Logs**: Look for error messages in application logs
2. **Verify Setup**: Ensure all files are in correct locations
3. **Test Fallbacks**: Verify API and pattern fallbacks work
4. **Performance**: Monitor memory and CPU usage
5. **Configuration**: Double-check environment variables

Your local VaultGemma-1B model is now ready for privacy-preserving document classification! 🚀🔒
