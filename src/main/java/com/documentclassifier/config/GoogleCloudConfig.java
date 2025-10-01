package com.documentclassifier.config;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.cloud.aiplatform.v1.PredictionServiceSettings;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
@Configuration
public class GoogleCloudConfig {
    private static final Logger logger = LoggerFactory.getLogger(GoogleCloudConfig.class);
    @Value("${google.cloud.project-id}")
    private String projectId;
    @Value("${google.cloud.location}")
    private String location;
    @Value("${gemini.model}")
    private String geminiModel;
    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        // Set quota project if not already set
        if (credentials.getQuotaProjectId() == null) {
            logger.info("Setting quota project to: {}", projectId);
            credentials = credentials.toBuilder()
                    .setQuotaProjectId(projectId)
                    .build();
        }
        return credentials;
    }
    @Bean
    public ImageAnnotatorClient imageAnnotatorClient(GoogleCredentials credentials) throws IOException {
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                .setCredentialsProvider(credentialsProvider)
                .build();
        logger.info("Creating ImageAnnotatorClient with project: {}", projectId);
        return ImageAnnotatorClient.create(settings);
    }
    @Bean
    public PredictionServiceClient predictionServiceClient(GoogleCredentials credentials) throws IOException {
        String endpoint = String.format("%s-aiplatform.googleapis.com:443", location);
        CredentialsProvider credentialsProvider = FixedCredentialsProvider.create(credentials);
        PredictionServiceSettings settings = PredictionServiceSettings.newBuilder()
                .setEndpoint(endpoint)
                .setCredentialsProvider(credentialsProvider)
                .build();
        logger.info("Creating PredictionServiceClient with endpoint: {} and project: {}", endpoint, projectId);
        return PredictionServiceClient.create(settings);
    }
    @Bean
    public String projectId() {
        return projectId;
    }
    @Bean
    public String location() {
        return location;
    }
    @Bean
    public VertexAI vertexAI(GoogleCredentials credentials) {
        logger.info("Creating VertexAI client with project: {} and location: {}", projectId, location);
        return new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation(location)
                .setCredentials(credentials)
                .build();
    }
    @Bean
    public GenerativeModel generativeModel(VertexAI vertexAI) {
        logger.info("Creating GenerativeModel with model: {}", geminiModel);
        return new GenerativeModel.Builder()
                .setModelName(geminiModel)
                .setVertexAi(vertexAI)
                .build();
    }
    
    @Bean
    public RestTemplate restTemplate() {
        logger.info("Creating RestTemplate for HTTP requests");
        return new RestTemplate();
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        logger.info("Creating ObjectMapper for JSON processing");
        return new ObjectMapper();
    }
}
