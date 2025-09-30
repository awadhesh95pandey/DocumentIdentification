package com.documentclassifier.vault;

import com.documentclassifier.config.VaultGemmaConfig;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

@Service
public class EncryptionService {
    
    private static final Logger logger = LoggerFactory.getLogger(EncryptionService.class);
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    private final VaultGemmaConfig config;
    private final SecureRandom secureRandom;
    private SecretKey masterKey;
    
    @Autowired
    public EncryptionService(VaultGemmaConfig config) {
        this.config = config;
        this.secureRandom = new SecureRandom();
        
        // Add BouncyCastle provider for enhanced cryptographic support
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        
        initializeMasterKey();
    }
    
    /**
     * Initialize or load the master encryption key
     */
    private void initializeMasterKey() {
        try {
            Path keyPath = Paths.get(config.getSecurity().getVault().getStoragePath(), "master.key");
            
            if (Files.exists(keyPath)) {
                // Load existing key
                byte[] keyBytes = Files.readAllBytes(keyPath);
                this.masterKey = new SecretKeySpec(keyBytes, "AES");
                logger.info("Loaded existing master key");
            } else {
                // Generate new key
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
                keyGenerator.init(config.getSecurity().getEncryption().getKeySize());
                this.masterKey = keyGenerator.generateKey();
                
                // Save key securely
                Files.createDirectories(keyPath.getParent());
                Files.write(keyPath, masterKey.getEncoded());
                
                // Set restrictive permissions (Unix-like systems)
                try {
                    Files.setPosixFilePermissions(keyPath, 
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                } catch (Exception e) {
                    logger.warn("Could not set file permissions: {}", e.getMessage());
                }
                
                logger.info("Generated and saved new master key");
            }
        } catch (Exception e) {
            logger.error("Failed to initialize master key", e);
            throw new RuntimeException("Encryption service initialization failed", e);
        }
    }
    
    /**
     * Encrypt sensitive text data
     */
    public String encryptText(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes("UTF-8"));
            
            // Combine IV and encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            logger.error("Failed to encrypt text", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt sensitive text data
     */
    public String decryptText(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and encrypted data
            byte[] iv = Arrays.copyOfRange(encryptedWithIv, 0, GCM_IV_LENGTH);
            byte[] encryptedData = Arrays.copyOfRange(encryptedWithIv, GCM_IV_LENGTH, encryptedWithIv.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, "UTF-8");
            
        } catch (Exception e) {
            logger.error("Failed to decrypt text", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    /**
     * Encrypt binary data (for files)
     */
    public byte[] encryptBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }
        
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(data);
            
            // Combine IV and encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);
            
            return encryptedWithIv;
            
        } catch (Exception e) {
            logger.error("Failed to encrypt bytes", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt binary data (for files)
     */
    public byte[] decryptBytes(byte[] encryptedData) {
        if (encryptedData == null || encryptedData.length == 0) {
            return encryptedData;
        }
        
        try {
            // Extract IV and encrypted data
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(encryptedData, GCM_IV_LENGTH, encryptedData.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);
            
            return cipher.doFinal(encrypted);
            
        } catch (Exception e) {
            logger.error("Failed to decrypt bytes", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    /**
     * Generate a secure hash for data integrity verification
     */
    public String generateHash(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("Failed to generate hash", e);
            throw new RuntimeException("Hash generation failed", e);
        }
    }
    
    /**
     * Securely clear sensitive data from memory
     */
    public void clearSensitiveData(byte[] data) {
        if (data != null) {
            Arrays.fill(data, (byte) 0);
        }
    }
    
    /**
     * Securely clear sensitive data from memory
     */
    public void clearSensitiveData(char[] data) {
        if (data != null) {
            Arrays.fill(data, '\0');
        }
    }
}
