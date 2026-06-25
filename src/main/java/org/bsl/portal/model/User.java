package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Document(collection = "users")
public class User {
    private static final String PERMISSION_NONE = "NONE";
    private static final String PERMISSION_NOTICE = "NOTICE";
    private static final String PERMISSION_DOCUMENT = "DOCUMENT";
    private static final String PERMISSION_BOTH_ALIAS = "BOTH";

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
     * Allowed values after normalize:
     * - NONE
     * - NOTICE
     * - DOCUMENT
     * - NOTICE,DOCUMENT
     *
     * Old value BOTH is still accepted and converted to NOTICE,DOCUMENT.
     */
    private String approvePermission = PERMISSION_NONE;

    /**
     * Booking permission for non-admin users.
     * Allowed values:
     * - NONE
     * - BOOKING
     */
    private String bookingPermission = "NONE";

    /**
     * Menu/module permission for non-admin users.
     * Allowed values after normalize:
     * - NONE
     * - NOTICE
     * - DOCUMENT
     * - NOTICE,DOCUMENT
     *
     * Admin role automatically has all menus.
     */
    private String modulePermission = PERMISSION_NONE;

    private String normalizeNoticeDocumentPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PERMISSION_NONE;
        }

        String normalized = value.trim().toUpperCase();
        Set<String> permissions = new LinkedHashSet<>();

        for (String item : normalized.split(",")) {
            String cleanItem = item.trim();

            if (PERMISSION_BOTH_ALIAS.equals(cleanItem) || "ALL".equals(cleanItem)) {
                permissions.add(PERMISSION_NOTICE);
                permissions.add(PERMISSION_DOCUMENT);
            } else if (PERMISSION_NOTICE.equals(cleanItem) || PERMISSION_DOCUMENT.equals(cleanItem)) {
                permissions.add(cleanItem);
            }
        }

        if (permissions.isEmpty()) {
            return PERMISSION_NONE;
        }

        return String.join(",", permissions);
    }

    private String normalizeBookingPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NONE";
        }

        String normalized = value.trim().toUpperCase();

        if ("BOOKING".equals(normalized) || "NONE".equals(normalized)) {
            return normalized;
        }

        return "NONE";
    }

    private List<String> toPermissionList(String value) {
        String normalized = normalizeNoticeDocumentPermission(value);
        List<String> result = new ArrayList<>();

        if (PERMISSION_NONE.equals(normalized)) {
            result.add(PERMISSION_NONE);
            return result;
        }

        result.addAll(Arrays.asList(normalized.split(",")));
        return result;
    }

    private boolean isAdminRole() {
        if (this.role == null || this.role.trim().isEmpty()) {
            return false;
        }

        String normalizedRole = this.role.trim().toUpperCase();

        return "ADMIN".equals(normalizedRole)
                || "ROLE_ADMIN".equals(normalizedRole);
    }

    private boolean hasApprovePermission(String target) {
        // Approval is controlled by approvePermission only.
        // Role Admin alone does not grant approve permission.
        return toPermissionList(this.approvePermission).contains(target);
    }

    private boolean hasModulePermission(String target) {
        if (isAdminRole()) {
            return true;
        }

        return toPermissionList(this.modulePermission).contains(target);
    }

    public boolean canApproveNotice() {
        return hasApprovePermission(PERMISSION_NOTICE);
    }

    public boolean canApproveDocument() {
        return hasApprovePermission(PERMISSION_DOCUMENT);
    }

    public boolean canManageBooking() {
        if (isAdminRole()) {
            return true;
        }

        String permission = normalizeBookingPermission(this.bookingPermission);
        return "BOOKING".equals(permission);
    }

    public boolean canManageNotice() {
        return hasModulePermission(PERMISSION_NOTICE);
    }

    public boolean canManageDocument() {
        return hasModulePermission(PERMISSION_DOCUMENT);
    }

    public boolean canManageDepartment() {
        return isAdminRole();
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
        return normalizeNoticeDocumentPermission(approvePermission);
    }

    public void setApprovePermission(String approvePermission) {
        this.approvePermission = normalizeNoticeDocumentPermission(approvePermission);
    }

    public List<String> getApprovePermissions() {
        return toPermissionList(this.approvePermission);
    }

    public void setApprovePermissions(List<String> approvePermissions) {
        if (approvePermissions == null || approvePermissions.isEmpty()) {
            this.approvePermission = PERMISSION_NONE;
            return;
        }

        this.approvePermission = normalizeNoticeDocumentPermission(String.join(",", approvePermissions));
    }

    public String getBookingPermission() {
        return normalizeBookingPermission(bookingPermission);
    }

    public void setBookingPermission(String bookingPermission) {
        this.bookingPermission = normalizeBookingPermission(bookingPermission);
    }

    public String getModulePermission() {
        return normalizeNoticeDocumentPermission(modulePermission);
    }

    public void setModulePermission(String modulePermission) {
        this.modulePermission = normalizeNoticeDocumentPermission(modulePermission);
    }

    public List<String> getModulePermissions() {
        return toPermissionList(this.modulePermission);
    }

    public void setModulePermissions(List<String> modulePermissions) {
        if (modulePermissions == null || modulePermissions.isEmpty()) {
            this.modulePermission = PERMISSION_NONE;
            return;
        }

        this.modulePermission = normalizeNoticeDocumentPermission(String.join(",", modulePermissions));
    }
}
