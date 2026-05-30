package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String username;
    private String email;
    private String password;
    private String address;
    private String phone;
    private String role;
    private LocalDateTime createdAt;
    private String profileImageUrl;
    private boolean isEnabled;
    private long tokenVersion;

    private String departmentId;

    /**
     * Approval permission for non-admin users.
     * Allowed values:
     * - NONE: no approval permission
     * - NOTICE: can approve notices
     * - DOCUMENT: can approve documents
     * - BOTH: can approve notices and documents
     */
    private String approvePermission = "NONE";

    private String normalizeApprovePermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NONE";
        }

        String normalized = value.trim().toUpperCase();

        if ("NOTICE".equals(normalized)
                || "DOCUMENT".equals(normalized)
                || "BOTH".equals(normalized)
                || "NONE".equals(normalized)) {
            return normalized;
        }

        return "NONE";
    }

    public boolean canApproveNotice() {
        String permission = normalizeApprovePermission(this.approvePermission);
        return "NOTICE".equals(permission) || "BOTH".equals(permission);
    }

    public boolean canApproveDocument() {
        String permission = normalizeApprovePermission(this.approvePermission);
        return "DOCUMENT".equals(permission) || "BOTH".equals(permission);
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getApprovePermission() {
        return normalizeApprovePermission(approvePermission);
    }

    public void setApprovePermission(String approvePermission) {
        this.approvePermission = normalizeApprovePermission(approvePermission);
    }
}
