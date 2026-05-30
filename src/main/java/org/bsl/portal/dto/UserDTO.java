package org.bsl.portal.dto;

import java.time.LocalDateTime;

public class UserDTO {
    private String id;
    private String username;
    private String email;
    private String address;
    private String phone;
    private String role;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private boolean enabled;
    private String departmentId;
    private String approvePermission = "NONE";
    private boolean canApproveNotice;
    private boolean canApproveDocument;

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

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean getEnabled() {
        return enabled;
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

    public boolean isCanApproveNotice() {
        return canApproveNotice;
    }

    public boolean getCanApproveNotice() {
        return canApproveNotice;
    }

    public void setCanApproveNotice(boolean canApproveNotice) {
        this.canApproveNotice = canApproveNotice;
    }

    public boolean isCanApproveDocument() {
        return canApproveDocument;
    }

    public boolean getCanApproveDocument() {
        return canApproveDocument;
    }

    public void setCanApproveDocument(boolean canApproveDocument) {
        this.canApproveDocument = canApproveDocument;
    }

    private String normalizeApprovePermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NONE";
        }

        String permission = value.trim().toUpperCase();

        if ("NONE".equals(permission)
                || "NOTICE".equals(permission)
                || "DOCUMENT".equals(permission)
                || "BOTH".equals(permission)) {
            return permission;
        }

        return "NONE";
    }
}
