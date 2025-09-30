package com.documentclassifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vaultgemma")
public class VaultGemmaConfig {
    
    private Model model = new Model();
    private Security security = new Security();
    
    public Model getModel() {
        return model;
    }
    
    public void setModel(Model model) {
        this.model = model;
    }
    
    public Security getSecurity() {
        return security;
    }
    
    public void setSecurity(Security security) {
        this.security = security;
    }
    
    public static class Model {
        private String path = "models/vaultgemma-1b";
        private int maxTokens = 1024;
        private double temperature = 0.1;
        private double privacyBudget = 1.0;
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public int getMaxTokens() {
            return maxTokens;
        }
        
        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
        
        public double getTemperature() {
            return temperature;
        }
        
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
        
        public double getPrivacyBudget() {
            return privacyBudget;
        }
        
        public void setPrivacyBudget(double privacyBudget) {
            this.privacyBudget = privacyBudget;
        }
    }
    
    public static class Security {
        private Encryption encryption = new Encryption();
        private Vault vault = new Vault();
        
        public Encryption getEncryption() {
            return encryption;
        }
        
        public void setEncryption(Encryption encryption) {
            this.encryption = encryption;
        }
        
        public Vault getVault() {
            return vault;
        }
        
        public void setVault(Vault vault) {
            this.vault = vault;
        }
        
        public static class Encryption {
            private String algorithm = "AES";
            private int keySize = 256;
            
            public String getAlgorithm() {
                return algorithm;
            }
            
            public void setAlgorithm(String algorithm) {
                this.algorithm = algorithm;
            }
            
            public int getKeySize() {
                return keySize;
            }
            
            public void setKeySize(int keySize) {
                this.keySize = keySize;
            }
        }
        
        public static class Vault {
            private String storagePath = "./secure-vault";
            private int retentionDays = 30;
            
            public String getStoragePath() {
                return storagePath;
            }
            
            public void setStoragePath(String storagePath) {
                this.storagePath = storagePath;
            }
            
            public int getRetentionDays() {
                return retentionDays;
            }
            
            public void setRetentionDays(int retentionDays) {
                this.retentionDays = retentionDays;
            }
        }
    }
}
