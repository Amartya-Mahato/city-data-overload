package com.lemillion.city_data_overload_server.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing user reports and tracking in Firestore.
 * Handles user collections, report history, and deletion capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserReportService {

    private final Firestore firestore;
    private final CloudStorageService cloudStorageService;
    
    private static final String USERS_COLLECTION = "users";
    private static final String USER_REPORTS_COLLECTION = "user_reports";

    /**
     * Store user report and update user's report history
     */
    public CompletableFuture<String> storeUserReport(String userId, String eventId, 
                                                    String content, double latitude, double longitude, 
                                                    String area, List<String> mediaUrls, 
                                                    String aiSummary, int ttlHours) {
        
        String reportId = UUID.randomUUID().toString();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Calculate TTL for user report
                Date ttlDate = new Date(System.currentTimeMillis() + (ttlHours * 60 * 60 * 1000L));
                
                // Create user report document
                Map<String, Object> userReport = new HashMap<>();
                userReport.put("id", reportId);
                userReport.put("userId", userId);
                userReport.put("eventId", eventId);
                userReport.put("content", content);
                userReport.put("latitude", latitude);
                userReport.put("longitude", longitude);
                userReport.put("area", area != null ? area : "");
                userReport.put("mediaUrls", mediaUrls != null ? mediaUrls : new ArrayList<>());
                userReport.put("aiSummary", aiSummary != null ? aiSummary : "");
                userReport.put("createdAt", new Date());
                userReport.put("ttl", ttlDate);
                userReport.put("status", "ACTIVE");
                
                // Store user report
                DocumentReference userReportRef = firestore.collection(USER_REPORTS_COLLECTION).document(reportId);
                userReportRef.set(userReport).get();
                
                // Update user document with report ID
                updateUserReportHistory(userId, reportId).get();
                
                log.info("User report stored successfully: {} for user: {}", reportId, userId);
                return reportId;
                
            } catch (Exception e) {
                log.error("Error storing user report for user: {}", userId, e);
                throw new RuntimeException("Failed to store user report", e);
            }
        });
    }

    /**
     * Get user's report history
     */
    public CompletableFuture<List<Map<String, Object>>> getUserReportHistory(String userId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Query query = firestore.collection(USER_REPORTS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("status", "ACTIVE")
                    .whereGreaterThan("ttl", new Date())
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit);
                
                QuerySnapshot querySnapshot = query.get().get();
                
                List<Map<String, Object>> reports = querySnapshot.getDocuments().stream()
                    .map(doc -> {
                        Map<String, Object> data = new HashMap<>(doc.getData());
                        data.put("reportId", doc.getId());
                        return data;
                    })
                    .collect(Collectors.toList());
                
                log.debug("Retrieved {} reports for user: {}", reports.size(), userId);
                return reports;
                
            } catch (Exception e) {
                log.error("Error getting user report history for user: {}", userId, e);
                throw new RuntimeException("Failed to get user reports", e);
            }
        });
    }

    /**
     * Delete user report (marks as deleted and removes media files)
     */
    public CompletableFuture<Boolean> deleteUserReport(String userId, String reportId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get report document
                DocumentSnapshot reportDoc = firestore.collection(USER_REPORTS_COLLECTION)
                    .document(reportId).get().get();
                
                if (!reportDoc.exists()) {
                    log.warn("Report not found: {}", reportId);
                    return false;
                }
                
                Map<String, Object> reportData = reportDoc.getData();
                String reportUserId = (String) reportData.get("userId");
                
                // Verify ownership
                if (!userId.equals(reportUserId)) {
                    log.warn("User {} attempted to delete report {} owned by {}", userId, reportId, reportUserId);
                    return false;
                }
                
                // Mark report as deleted
                firestore.collection(USER_REPORTS_COLLECTION)
                    .document(reportId)
                    .update("status", "DELETED", "deletedAt", new Date())
                    .get();
                
                // Delete media files from Cloud Storage
                @SuppressWarnings("unchecked")
                List<String> mediaUrls = (List<String>) reportData.getOrDefault("mediaUrls", new ArrayList<>());
                
                for (String mediaUrl : mediaUrls) {
                    cloudStorageService.deleteFile(mediaUrl)
                        .exceptionally(throwable -> {
                            log.warn("Failed to delete media file: {}", mediaUrl, throwable);
                            return false;
                        });
                }
                
                // Update user document to remove report ID
                removeReportFromUser(userId, reportId).get();
                
                log.info("User report deleted successfully: {} by user: {}", reportId, userId);
                return true;
                
            } catch (Exception e) {
                log.error("Error deleting user report: {} for user: {}", reportId, userId, e);
                return false;
            }
        });
    }

    /**
     * Get user statistics
     */
    public CompletableFuture<Map<String, Object>> getUserStatistics(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get user document
                DocumentSnapshot userDoc = firestore.collection(USERS_COLLECTION)
                    .document(userId).get().get();
                
                Map<String, Object> stats = new HashMap<>();
                stats.put("userId", userId);
                stats.put("exists", userDoc.exists());
                
                if (userDoc.exists()) {
                    Map<String, Object> userData = userDoc.getData();
                    @SuppressWarnings("unchecked")
                    List<String> reportIds = (List<String>) userData.getOrDefault("reportIds", new ArrayList<>());
                    
                    stats.put("totalReports", reportIds.size());
                    stats.put("joinedAt", userData.get("createdAt"));
                    stats.put("lastActive", userData.get("lastActiveAt"));
                    
                    // Get active reports count
                    long activeReports = firestore.collection(USER_REPORTS_COLLECTION)
                        .whereEqualTo("userId", userId)
                        .whereEqualTo("status", "ACTIVE")
                        .whereGreaterThan("ttl", new Date())
                        .get().get().size();
                    
                    stats.put("activeReports", activeReports);
                } else {
                    stats.put("totalReports", 0);
                    stats.put("activeReports", 0);
                }
                
                return stats;
                
            } catch (Exception e) {
                log.error("Error getting user statistics for user: {}", userId, e);
                throw new RuntimeException("Failed to get user statistics", e);
            }
        });
    }

    /**
     * Update user document with new report ID
     */
    private CompletableFuture<Void> updateUserReportHistory(String userId, String reportId) {
        return CompletableFuture.runAsync(() -> {
            try {
                DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
                
                // Get or create user document
                DocumentSnapshot userDoc = userRef.get().get();
                
                if (userDoc.exists()) {
                    // Update existing user
                    userRef.update(
                        "reportIds", FieldValue.arrayUnion(reportId),
                        "lastActiveAt", new Date(),
                        "totalReports", FieldValue.increment(1)
                    ).get();
                } else {
                    // Create new user document
                    Map<String, Object> userData = Map.of(
                        "userId", userId,
                        "reportIds", Arrays.asList(reportId),
                        "createdAt", new Date(),
                        "lastActiveAt", new Date(),
                        "totalReports", 1,
                        "status", "ACTIVE"
                    );
                    userRef.set(userData).get();
                }
                
                log.debug("Updated user report history for user: {}", userId);
                
            } catch (Exception e) {
                log.error("Error updating user report history for user: {}", userId, e);
                throw new RuntimeException("Failed to update user history", e);
            }
        });
    }

    /**
     * Remove report ID from user document
     */
    private CompletableFuture<Void> removeReportFromUser(String userId, String reportId) {
        return CompletableFuture.runAsync(() -> {
            try {
                DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
                
                userRef.update(
                    "reportIds", FieldValue.arrayRemove(reportId),
                    "lastActiveAt", new Date(),
                    "totalReports", FieldValue.increment(-1)
                ).get();
                
                log.debug("Removed report {} from user: {}", reportId, userId);
                
            } catch (Exception e) {
                log.error("Error removing report from user: {}", userId, e);
                throw new RuntimeException("Failed to remove report from user", e);
            }
        });
    }

    /**
     * Clean up expired user reports
     */
    public CompletableFuture<Integer> cleanupExpiredUserReports() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Query expiredQuery = firestore.collection(USER_REPORTS_COLLECTION)
                    .whereLessThan("ttl", new Date())
                    .whereEqualTo("status", "ACTIVE")
                    .limit(100);
                
                QuerySnapshot querySnapshot = expiredQuery.get().get();
                
                if (querySnapshot.isEmpty()) {
                    return 0;
                }
                
                WriteBatch batch = firestore.batch();
                int deleteCount = 0;
                
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Map<String, Object> data = doc.getData();
                    String userId = (String) data.get("userId");
                    String reportId = doc.getId();
                    
                    // Mark as expired
                    batch.update(doc.getReference(), "status", "EXPIRED");
                    
                    // Remove from user document
                    DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(userId);
                    batch.update(userRef, 
                        "reportIds", FieldValue.arrayRemove(reportId),
                        "totalReports", FieldValue.increment(-1)
                    );
                    
                    deleteCount++;
                }
                
                batch.commit().get();
                
                log.info("Cleaned up {} expired user reports", deleteCount);
                return deleteCount;
                
            } catch (Exception e) {
                log.error("Error cleaning up expired user reports", e);
                throw new RuntimeException("Cleanup failed", e);
            }
        });
    }
} 