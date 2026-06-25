package org.bsl.portal.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserDTO {
    private static final String PERMISSION_NONE = "NONE";
    private static final String PERMISSION_NOTICE = "NOTICE";
    private static final String PERMISSION_DOCUMENT = "DOCUMENT";

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

    private String approvePermission = PERMISSION_NONE;
    private List<String> approvePermissions = new ArrayList<>();
    private boolean canApproveNotice;
    private boolean canApproveDocument;

    private String bookingPermission = "NONE";
    private boolean canManageBooking;

    private String modulePermission = PERMISSION_NONE;
    private List<String> modulePermissions = new ArrayList<>();
    private boolean canManageNotice;
    private boolean canManageDocument;
    private boolean canManageDepartment;

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
        return normalizeNoticeDocumentPermission(approvePermission);
    }

    public void setApprovePermission(String approvePermission) {
        this.approvePermission = normalizeNoticeDocumentPermission(approvePermission);
        this.approvePermissions = toPermissionList(this.approvePermission);
    }

    public List<String> getApprovePermissions() {
        return toPermissionList(this.approvePermission);
    }

    public void setApprovePermissions(List<String> approvePermissions) {
        this.approvePermissions = approvePermissions != null ? new ArrayList<>(approvePermissions) : new ArrayList<>();
        this.approvePermission = normalizeNoticeDocumentPermissions(this.approvePermissions);
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

    public String getModulePermission() {
        return normalizeNoticeDocumentPermission(modulePermission);
    }

    public void setModulePermission(String modulePermission) {
        this.modulePermission = normalizeNoticeDocumentPermission(modulePermission);
        this.modulePermissions = toPermissionList(this.modulePermission);
    }

    public List<String> getModulePermissions() {
        return toPermissionList(this.modulePermission);
    }

    public void setModulePermissions(List<String> modulePermissions) {
        this.modulePermissions = modulePermissions != null ? new ArrayList<>(modulePermissions) : new ArrayList<>();
        this.modulePermission = normalizeNoticeDocumentPermissions(this.modulePermissions);
    }

    public boolean isCanManageNotice() {
        return canManageNotice;
    }

    public boolean getCanManageNotice() {
        return canManageNotice;
    }

    public void setCanManageNotice(boolean canManageNotice) {
        this.canManageNotice = canManageNotice;
    }

    public boolean isCanManageDocument() {
        return canManageDocument;
    }

    public boolean getCanManageDocument() {
        return canManageDocument;
    }

    public void setCanManageDocument(boolean canManageDocument) {
        this.canManageDocument = canManageDocument;
    }

    public boolean isCanManageDepartment() {
        return canManageDepartment;
    }

    public boolean getCanManageDepartment() {
        return canManageDepartment;
    }

    public void setCanManageDepartment(boolean canManageDepartment) {
        this.canManageDepartment = canManageDepartment;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNoticeDocumentPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PERMISSION_NONE;
        }

        String permission = value.trim().toUpperCase();
        Set<String> permissions = new LinkedHashSet<>();

        for (String item : permission.split(",")) {
            String cleanItem = item.trim();

            if ("BOTH".equals(cleanItem) || "ALL".equals(cleanItem)) {
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

    private String normalizeNoticeDocumentPermissions(List<String> values) {
        if (values == null || values.isEmpty()) {
            return PERMISSION_NONE;
        }

        return normalizeNoticeDocumentPermission(
                values.stream()
                        .filter(item -> item != null && !item.trim().isEmpty())
                        .collect(Collectors.joining(","))
        );
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
