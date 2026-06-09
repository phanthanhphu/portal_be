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
    private String departmentName;
    private String division;

    private String approvePermission = "NONE";
    private boolean canApproveNotice;
    private boolean canApproveDocument;

    private String bookingPermission = "NONE";
    private boolean canManageBooking;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = clean(id);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = clean(username);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = clean(email);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = clean(address);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = clean(phone);
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = clean(role);
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = clean(profileImageUrl);
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

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = clean(departmentId);
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = clean(departmentName);
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = clean(division);
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

    public String getBookingPermission() {
        return normalizeBookingPermission(bookingPermission);
    }

    public void setBookingPermission(String bookingPermission) {
        this.bookingPermission = normalizeBookingPermission(bookingPermission);
    }

    public boolean isCanManageBooking() {
        return canManageBooking;
    }

    public boolean getCanManageBooking() {
        return canManageBooking;
    }

    public void setCanManageBooking(boolean canManageBooking) {
        this.canManageBooking = canManageBooking;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
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

    private String normalizeBookingPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NONE";
        }

        String permission = value.trim().toUpperCase();

        if ("NONE".equals(permission) || "BOOKING".equals(permission)) {
            return permission;
        }

        return "NONE";
    }
}
