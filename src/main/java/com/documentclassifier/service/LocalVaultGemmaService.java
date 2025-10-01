package com.documentclassifier.service;

import com.documentclassifier.config.VaultGemmaConfig;
import com.documentclassifier.vault.AuditService;
import ai.onnxruntime.*;
import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LocalVaultGemmaService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalVaultGemmaService.class);
    
    private final VaultGemmaConfig config;
    private final AuditService auditService;
    private final SecureRandom secureRandom;
    
    // Model components
    private OrtSession ortSession;
    private HuggingFaceTokenizer tokenizer;
    private boolean modelLoaded = false;
    private final Object modelLock = new Object();
    
    // Performance tracking
    private final Map<String, Long> inferenceMetrics = new ConcurrentHashMap<>();
    private long totalInferences = 0;
    private long totalInferenceTime = 0;
    
    // Classification labels for VaultGemma document classification
    private final String[] CLASSIFICATION_LABELS = {
        "Aadhaar", "PAN", "Voter ID", "Driving License", "Passport", "None"
    };
    
    @Autowired
    public LocalVaultGemmaService(VaultGemmaConfig config, AuditService auditService) {
        this.config = config;
        this.auditService = auditService;
        this.secureRandom = new SecureRandom();
        
        // Initialize model on startup
        initializeModel();
    }
    
    /**
     * Initialize the local VaultGemma model
     */
    private void initializeModel() {
        synchronized (modelLock) {
            try {
                logger.info("Initializing local VaultGemma model...");
                
                Path modelPath = Paths.get(config.getModel().getPath());
                if (!Files.exists(modelPath)) {
                    logger.warn("Local VaultGemma model directory not found: {}", modelPath.toAbsolutePath());
                    return;
                }
                
                // Load ONNX model
                Path onnxModelPath = modelPath.resolve("model.onnx");
                if (Files.exists(onnxModelPath)) {
                    loadOnnxModel(onnxModelPath);
                } else {
                    logger.warn("ONNX model file not found: {}", onnxModelPath.toAbsolutePath());
                }
                
                // Load tokenizer
                Path tokenizerPath = modelPath.resolve("tokenizer.json");
                if (Files.exists(tokenizerPath)) {
                    loadTokenizer(tokenizerPath);
                } else {
                    logger.warn("Tokenizer file not found: {}", tokenizerPath.toAbsolutePath());
                }
                
                if (ortSession != null && tokenizer != null) {
                    modelLoaded = true;
                    logger.info("✅ Local VaultGemma model loaded successfully!");
                    auditService.logSecurityEvent("SYSTEM", "MODEL_LOADED", "INFO", true, 
                                                "Local VaultGemma model initialized successfully");
                } else {
                    logger.warn("❌ Failed to load local VaultGemma model components");
                }
                
            } catch (Exception e) {
                logger.error("Failed to initialize local VaultGemma model", e);
                auditService.logSecurityEvent("SYSTEM", "MODEL_LOAD_FAILED", "HIGH", false, 
                                            "Local VaultGemma model initialization failed: " + e.getMessage());
            }
        }
    }
    
    /**
     * Load ONNX model
     */
    private void loadOnnxModel(Path onnxModelPath) throws OrtException {
        logger.info("Loading ONNX model from: {}", onnxModelPath.toAbsolutePath());
        
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
        
        // Configure session options for optimal performance
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        sessionOptions.setIntraOpNumThreads(config.getModel().getThreads());
        sessionOptions.setMemoryPatternOptimization(true);
        
        ortSession = env.createSession(onnxModelPath.toString(), sessionOptions);
        logger.info("ONNX model loaded with {} input(s) and {} output(s)", 
                   ortSession.getInputNames().size(), ortSession.getOutputNames().size());
    }
    
    /**
     * Load Hugging Face tokenizer
     */
    private void loadTokenizer(Path tokenizerPath) throws IOException {
        logger.info("Loading tokenizer from: {}", tokenizerPath.toAbsolutePath());
        tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
        logger.info("Tokenizer loaded successfully");
    }
    
    /**
     * Check if local model is available and loaded
     */
    public boolean isModelAvailable() {
        return modelLoaded && ortSession != null && tokenizer != null;
    }
    
    /**
     * Classify document with local VaultGemma model
     */
    public String classifyDocumentWithPrivacy(String text, String userId) {
        if (!isModelAvailable()) {
            logger.warn("Local VaultGemma model not available for user: {}", userId);
            throw new RuntimeException("Local VaultGemma model not loaded");
        }
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Preprocess text for privacy
            String processedText = preprocessTextForPrivacy(text);
            
            // Tokenize input
            Encoding encoding = tokenizer.encode(processedText);
            long[] inputIds = encoding.getIds();
            long[] attentionMask = encoding.getAttentionMask();
            
            // Limit sequence length for privacy and performance
            int maxLength = Math.min(config.getModel().getMaxTokens(), 512);
            inputIds = Arrays.copyOf(inputIds, Math.min(inputIds.length, maxLength));
            attentionMask = Arrays.copyOf(attentionMask, Math.min(attentionMask.length, maxLength));
            
            // Pad sequences if needed
            if (inputIds.length < maxLength) {
                inputIds = padSequence(inputIds, maxLength, 0L);
                attentionMask = padSequence(attentionMask, maxLength, 0L);
            }
            
            // Run inference
            String classification = runInference(inputIds, attentionMask, userId);
            
            // Apply differential privacy
            classification = applyDifferentialPrivacy(classification, userId);
            
            // Track performance metrics
            long inferenceTime = System.currentTimeMillis() - startTime;
            updateMetrics(inferenceTime);
            
            logger.debug("Local VaultGemma classification completed in {}ms for user: {}", 
                        inferenceTime, userId);
            
            auditService.logPrivacyEvent(userId, "LOCAL_MODEL_CLASSIFICATION", "HIGH", 
                                       "Document classified using local VaultGemma model");
            
            return classification;
            
        } catch (Exception e) {
            logger.error("Local VaultGemma inference failed for user {}: {}", userId, e.getMessage());
            auditService.logSecurityEvent(userId, "LOCAL_MODEL_ERROR", "HIGH", false, 
                                        "Local VaultGemma inference failed: " + e.getMessage());
            throw new RuntimeException("Local VaultGemma inference failed", e);
        }
    }
    
    /**
     * Preprocess text for privacy-preserving inference
     */
    private String preprocessTextForPrivacy(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // Limit text length for privacy
        String processedText = text.length() > 500 ? text.substring(0, 500) : text;
        
        // Remove potential PII patterns (basic sanitization)
        processedText = processedText.replaceAll("\\b\\d{12}\\b", "[REDACTED_NUMBER]"); // Aadhaar-like numbers
        processedText = processedText.replaceAll("\\b[A-Z]{5}\\d{4}[A-Z]\\b", "[REDACTED_PAN]"); // PAN-like patterns
        
        return processedText.trim();
    }
    
    /**
     * Run ONNX model inference
     */
    private String runInference(long[] inputIds, long[] attentionMask, String userId) throws OrtException {
        synchronized (modelLock) {
            if (ortSession == null) {
                throw new RuntimeException("ONNX session not initialized");
            }
            
            // Create input tensors
            long[] shape = {1, inputIds.length};
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), 
                                                              new long[][]{inputIds});
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), 
                                                                   new long[][]{attentionMask});
            
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            
            try {
                // Run inference
                OrtSession.Result result = ortSession.run(inputs);
                
                // Get output logits
                float[][] logits = (float[][]) result.get(0).getValue();
                
                // Apply softmax and get prediction
                float[] probabilities = softmax(logits[0]);
                int predictedClass = argmax(probabilities);
                
                // Map to classification label
                String classification = predictedClass < CLASSIFICATION_LABELS.length ? 
                                      CLASSIFICATION_LABELS[predictedClass] : "None";
                
                logger.debug("Local model prediction: {} (confidence: {:.3f})", 
                           classification, probabilities[predictedClass]);
                
                return classification;
                
            } finally {
                // Clean up tensors
                inputIdsTensor.close();
                attentionMaskTensor.close();
            }
        }
    }
    
    /**
     * Apply differential privacy to classification result
     */
    private String applyDifferentialPrivacy(String classification, String userId) {
        double epsilon = config.getModel().getEpsilonPerQuery();
        
        // Calculate noise probability based on epsilon
        double noiseProbability = Math.exp(-epsilon) / (Math.exp(-epsilon) + CLASSIFICATION_LABELS.length - 1);
        
        if (secureRandom.nextDouble() < noiseProbability) {
            // Add noise by returning a random classification
            String noisyClassification = CLASSIFICATION_LABELS[secureRandom.nextInt(CLASSIFICATION_LABELS.length)];
            
            auditService.logPrivacyEvent(userId, "DIFFERENTIAL_PRIVACY_NOISE", "HIGH", 
                                       "Applied differential privacy noise to local model result");
            
            logger.debug("Applied differential privacy noise: {} -> {}", classification, noisyClassification);
            return noisyClassification;
        }
        
        return classification;
    }
    
    /**
     * Pad sequence to target length
     */
    private long[] padSequence(long[] sequence, int targetLength, long padValue) {
        if (sequence.length >= targetLength) {
            return sequence;
        }
        
        long[] padded = new long[targetLength];
        System.arraycopy(sequence, 0, padded, 0, sequence.length);
        Arrays.fill(padded, sequence.length, targetLength, padValue);
        return padded;
    }
    
    /**
     * Apply softmax to logits
     */
    private float[] softmax(float[] logits) {
        float max = Arrays.stream(logits).max().orElse(0f);
        float[] exp = new float[logits.length];
        float sum = 0f;
        
        for (int i = 0; i < logits.length; i++) {
            exp[i] = (float) Math.exp(logits[i] - max);
            sum += exp[i];
        }
        
        for (int i = 0; i < exp.length; i++) {
            exp[i] /= sum;
        }
        
        return exp;
    }
    
    /**
     * Get index of maximum value
     */
    private int argmax(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        
        return maxIndex;
    }
    
    /**
     * Update performance metrics
     */
    private void updateMetrics(long inferenceTime) {
        totalInferences++;
        totalInferenceTime += inferenceTime;
        inferenceMetrics.put("lastInferenceTime", inferenceTime);
        inferenceMetrics.put("averageInferenceTime", totalInferenceTime / totalInferences);
        inferenceMetrics.put("totalInferences", totalInferences);
    }
    
    /**
     * Get model performance metrics
     */
    public Map<String, Object> getModelMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("modelLoaded", modelLoaded);
        metrics.put("modelAvailable", isModelAvailable());
        metrics.put("totalInferences", totalInferences);
        metrics.put("averageInferenceTime", totalInferences > 0 ? totalInferenceTime / totalInferences : 0);
        metrics.put("lastInferenceTime", inferenceMetrics.getOrDefault("lastInferenceTime", 0L));
        
        if (ortSession != null) {
            try {
                metrics.put("inputNames", ortSession.getInputNames());
                metrics.put("outputNames", ortSession.getOutputNames());
            } catch (Exception e) {
                logger.debug("Could not retrieve session info: {}", e.getMessage());
            }
        }
        
        return metrics;
    }
    
    /**
     * Validate model health
     */
    public boolean validateModelHealth() {
        if (!isModelAvailable()) {
            return false;
        }
        
        try {
            // Perform a simple test inference
            String testResult = classifyDocumentWithPrivacy("test document", "health-check");
            return testResult != null && !testResult.isEmpty();
        } catch (Exception e) {
            logger.warn("Model health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        synchronized (modelLock) {
            try {
                if (ortSession != null) {
                    ortSession.close();
                    ortSession = null;
                }
                if (tokenizer != null) {
                    tokenizer.close();
                    tokenizer = null;
                }
                modelLoaded = false;
                logger.info("Local VaultGemma model resources cleaned up");
            } catch (Exception e) {
                logger.error("Error cleaning up model resources", e);
            }
        }
    }
}
