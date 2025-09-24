package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * Configuration class for file upload settings and multipart resolver.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
public class FileUploadConfig {

    private String uploadDir = "./uploads";
    private String tempDir = "./temp";
    private int maxZipEntries = 1000;
    private String maxZipSize = "100MB";
    private String allowedFileTypes = "zip,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,rtf,odt,ods,odp";

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    // Getters and Setters
    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public int getMaxZipEntries() {
        return maxZipEntries;
    }

    public void setMaxZipEntries(int maxZipEntries) {
        this.maxZipEntries = maxZipEntries;
    }

    public String getMaxZipSize() {
        return maxZipSize;
    }

    public void setMaxZipSize(String maxZipSize) {
        this.maxZipSize = maxZipSize;
    }

    public String getAllowedFileTypes() {
        return allowedFileTypes;
    }

    public void setAllowedFileTypes(String allowedFileTypes) {
        this.allowedFileTypes = allowedFileTypes;
    }
}
