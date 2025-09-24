package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main Spring Boot application class for Document Extraction Service.
 * This application provides REST APIs for uploading ZIP files and extracting
 * documents from them using Apache Tika.
 */
@SpringBootApplication
@EnableConfigurationProperties
public class DocumentExtractionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentExtractionApplication.class, args);
        System.out.println("🚀 Document Extraction Service started successfully!");
        System.out.println("📁 Upload ZIP files to extract documents at: http://localhost:8080/api/upload");
    }
}
