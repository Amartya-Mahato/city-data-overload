package com.lemillion.city_data_overload_server.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.vertexai.VertexAI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Google Cloud Platform Configuration
 * 
 * Configures all GCP services including BigQuery, Firestore, Cloud Storage, and Vertex AI
 * for the City Data Overload application.
 */
@Configuration
@Slf4j
public class GcpConfiguration {

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.location}")
    private String location;

    @Value("${gcp.credentials-path}")
    private String credentialsPath;

    @Value("${gcp.storage.bucket-name}")
    private String storageBucketName;

    /**
     * Creates GoogleCredentials bean from service account key file.
     */
    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        log.info("Initializing Google Credentials...");
        
        // Method 1: Try environment variable first (most reliable)
        String serviceAccountJson = System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON");
        if (serviceAccountJson != null && !serviceAccountJson.trim().isEmpty()) {
            try {
                log.info("Loading credentials from GOOGLE_SERVICE_ACCOUNT_JSON environment variable...");
                java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(
                    serviceAccountJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
                
                try {
                    credentials.refresh();
                    log.info("✅ Successfully loaded and refreshed credentials from environment variable");
                    return credentials;
                } catch (Exception refreshEx) {
                    log.warn("⚠️ Loaded from env var but refresh failed: {}", refreshEx.getMessage());
                    return credentials; // Still return it, might work later
                }
            } catch (Exception e) {
                log.error("❌ Failed to load credentials from environment variable: {}", e.getMessage());
            }
        }
        
        // Method 2: Try file-based credentials
        try {
            log.info("Loading credentials from file: {}", credentialsPath);
            java.io.InputStream credentialStream = getClass().getClassLoader().getResourceAsStream(credentialsPath);
            if (credentialStream != null) {
                log.info("Successfully found credentials in classpath, loading...");
                GoogleCredentials credentials = GoogleCredentials.fromStream(credentialStream)
                    .createScoped("https://www.googleapis.com/auth/cloud-platform");
                
                try {
                    credentials.refresh();
                    log.info("✅ Successfully loaded and refreshed file-based credentials");
                    return credentials;
                } catch (Exception refreshEx) {
                    log.error("❌ File credentials loaded but refresh failed: {}", refreshEx.getMessage());
                    log.error("❌ This suggests the service account is disabled, deleted, or lacks permissions");
                    // Don't return broken credentials, try fallback
                }
            } else {
                log.error("❌ Credential file not found in classpath: {}", credentialsPath);
            }
        } catch (Exception e) {
            log.error("❌ Failed to load file-based credentials: {}", e.getMessage());
        }
        
        // Method 3: Fallback to Application Default Credentials
        try {
            log.info("Attempting to use Application Default Credentials as fallback...");
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
            
            try {
                credentials.refresh();
                log.info("✅ Successfully loaded and refreshed Application Default Credentials");
                return credentials;
            } catch (Exception refreshEx) {
                log.warn("⚠️ ADC loaded but refresh failed: {}", refreshEx.getMessage());
                return credentials; // Still return it
            }
        } catch (Exception adcEx) {
            log.error("❌ Failed to load Application Default Credentials: {}", adcEx.getMessage());
        }
        
        // Method 4: Create mock credentials for development
        log.error("❌ All credential loading methods failed");
        log.warn("🔧 Creating mock credentials for development (GCP services will not work)");
        log.warn("🔧 To fix this issue:");
        log.warn("   1. Check Google Cloud Console - ensure service account exists and is enabled");
        log.warn("   2. Verify service account has required IAM roles");
        log.warn("   3. Ensure project billing is enabled");
        log.warn("   4. Check if required APIs are enabled");
        return createMockCredentials();
    }
    
    /**
     * Creates mock credentials for development when real credentials are not available
     */
    private GoogleCredentials createMockCredentials() {
        log.warn("Using mock credentials - GCP services will not be functional");
        try {
            // Create a minimal service account credential that allows the app to start
            return GoogleCredentials.newBuilder().build();
        } catch (Exception e) {
            log.error("Failed to create mock credentials", e);
            throw new RuntimeException("Could not create any credentials", e);
        }
    }

    /**
     * Creates BigQuery client bean for data warehousing operations.
     */
    @Bean
    public BigQuery bigQuery(GoogleCredentials credentials) {
        log.info("Initializing BigQuery client for project: {}", projectId);
        try {
            return BigQueryOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(credentials)
                    .build()
                    .getService();
        } catch (Exception e) {
            log.error("Failed to initialize BigQuery client", e);
            throw new RuntimeException("BigQuery initialization failed", e);
        }
    }

    /**
     * Creates Firestore client bean for real-time database operations.
     */
    @Bean
    public Firestore firestore(GoogleCredentials credentials) {
        log.info("Initializing Firestore client for project: {}", projectId);
        return FirestoreOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }

    /**
     * Creates Cloud Storage client bean for file storage operations.
     */
    @Bean
    public Storage storage(GoogleCredentials credentials) {
        log.info("Initializing Cloud Storage client for project: {}", projectId);
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .setCredentials(credentials)
                .build()
                .getService();
    }

    /**
     * Creates Vertex AI client bean for AI/ML operations.
     */
    @Bean
    public VertexAI vertexAI(GoogleCredentials credentials) {
        log.info("Initializing Vertex AI client for project: {} in location: {}", projectId, location);
        return new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation(location)
                .setCredentials(credentials)
                .build();
    }

    /**
     * Gets the configured storage bucket name.
     */
    public String getStorageBucketName() {
        return storageBucketName;
    }

    /**
     * Gets the configured project ID.
     */
    public String getProjectId() {
        return projectId;
    }

    /**
     * Gets the configured location.
     */
    public String getLocation() {
        return location;
    }
} 