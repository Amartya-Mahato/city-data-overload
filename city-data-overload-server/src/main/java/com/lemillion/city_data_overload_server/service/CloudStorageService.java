package com.lemillion.city_data_overload_server.service;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Google Cloud Storage service for handling media file uploads.
 * Manages image and video storage for citizen reports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudStorageService {

    private final Storage storage;
    
    @Value("${gcp.storage.bucket-name}")
    private String bucketName;

    /**
     * Upload image file to Cloud Storage
     */
    public CompletableFuture<String> uploadImage(MultipartFile image, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String fileName = generateFileName(image.getOriginalFilename(), userId, "images");
                String contentType = image.getContentType();
                
                // Validate image type
                if (contentType == null || !contentType.startsWith("image/")) {
                    throw new IllegalArgumentException("Invalid image file type: " + contentType);
                }
                
                BlobId blobId = BlobId.of(bucketName, fileName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .setMetadata(java.util.Map.of(
                        "userId", userId,
                        "uploadType", "citizen_report_image",
                        "uploadTimestamp", String.valueOf(System.currentTimeMillis())
                    ))
                    .build();
                
                Blob blob = storage.create(blobInfo, image.getBytes());
                String publicUrl = String.format("gs://%s/%s", bucketName, fileName);
                
                log.info("Image uploaded successfully: {} (size: {} bytes)", publicUrl, image.getSize());
                return publicUrl;
                
            } catch (IOException e) {
                log.error("Error uploading image to Cloud Storage", e);
                throw new RuntimeException("Failed to upload image", e);
            }
        });
    }

    /**
     * Upload video file to Cloud Storage
     */
    public CompletableFuture<String> uploadVideo(MultipartFile video, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String fileName = generateFileName(video.getOriginalFilename(), userId, "videos");
                String contentType = video.getContentType();
                
                // Validate video type
                if (contentType == null || !contentType.startsWith("video/")) {
                    throw new IllegalArgumentException("Invalid video file type: " + contentType);
                }
                
                // Check file size (limit to 50MB for videos)
                if (video.getSize() > 50 * 1024 * 1024) {
                    throw new IllegalArgumentException("Video file too large. Maximum size is 50MB");
                }
                
                BlobId blobId = BlobId.of(bucketName, fileName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType)
                    .setMetadata(java.util.Map.of(
                        "userId", userId,
                        "uploadType", "citizen_report_video",
                        "uploadTimestamp", String.valueOf(System.currentTimeMillis())
                    ))
                    .build();
                
                Blob blob = storage.create(blobInfo, video.getBytes());
                String publicUrl = String.format("gs://%s/%s", bucketName, fileName);
                
                log.info("Video uploaded successfully: {} (size: {} bytes)", publicUrl, video.getSize());
                return publicUrl;
                
            } catch (IOException e) {
                log.error("Error uploading video to Cloud Storage", e);
                throw new RuntimeException("Failed to upload video", e);
            }
        });
    }

    /**
     * Delete file from Cloud Storage (for report deletion)
     */
    public CompletableFuture<Boolean> deleteFile(String fileUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Extract file path from gs:// URL
                String filePath = fileUrl.replace("gs://" + bucketName + "/", "");
                BlobId blobId = BlobId.of(bucketName, filePath);
                
                boolean deleted = storage.delete(blobId);
                
                if (deleted) {
                    log.info("File deleted successfully: {}", fileUrl);
                } else {
                    log.warn("File not found or already deleted: {}", fileUrl);
                }
                
                return deleted;
                
            } catch (Exception e) {
                log.error("Error deleting file from Cloud Storage: {}", fileUrl, e);
                return false;
            }
        });
    }

    /**
     * Generate unique file name with proper structure
     */
    private String generateFileName(String originalFileName, String userId, String folder) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFileName);
        
        return String.format("reports/%s/%s/%s_%s_%s%s", 
            folder, userId, timestamp, uuid, "report", extension);
    }

    /**
     * Extract file extension from filename
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        
        return fileName.substring(lastDotIndex);
    }

    /**
     * Get file info for verification
     */
    public CompletableFuture<java.util.Map<String, Object>> getFileInfo(String fileUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filePath = fileUrl.replace("gs://" + bucketName + "/", "");
                BlobId blobId = BlobId.of(bucketName, filePath);
                Blob blob = storage.get(blobId);
                
                if (blob == null) {
                    return java.util.Map.of("exists", false);
                }
                
                return java.util.Map.of(
                    "exists", true,
                    "size", blob.getSize(),
                    "contentType", blob.getContentType(),
                    "created", blob.getCreateTime(),
                    "updated", blob.getUpdateTime(),
                    "metadata", blob.getMetadata()
                );
                
            } catch (Exception e) {
                log.error("Error getting file info: {}", fileUrl, e);
                return java.util.Map.of("exists", false, "error", e.getMessage());
            }
        });
    }
} 