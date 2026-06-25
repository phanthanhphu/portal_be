package org.bsl.portal.request;

import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserRequest {
    public static final String PERMISSION_NONE = "NONE";
    public static final String PERMISSION_NOTICE = "NOTICE";
    public static final String PERMISSION_DOCUMENT = "DOCUMENT";

    // Giữ lại alias cũ để dữ liệu/API cũ không bị lỗi
    public static final String APPROVE_NONE = PERMISSION_NONE;
    public static final String APPROVE_NOTICE = PERMISSION_NOTICE;
    public static final String APPROVE_DOCUMENT = PERMISSION_DOCUMENT;
    public static final String APPROVE_BOTH = "BOTH";

    public static final String BOOKING_NONE = "NONE";
    public static final String BOOKING_MANAGE = "BOOKING";

    public static final String MODULE_NONE = PERMISSION_NONE;
    public static final String MODULE_NOTICE = PERMISSION_NOTICE;
    public static final String MODULE_DOCUMENT = PERMISSION_DOCUMENT;

    private String username;
    private String email;
    private String password;
    private String address;
    private String phone;
    private String role;
    private Boolean isEnabled;
    private String departmentId;

    /**
     * Checkbox permissions for approval features.
     * FE có thể gửi:
     * - approvePermission=NONE
     * - approvePermission=NOTICE
     * - approvePermission=DOCUMENT
     * - approvePermission=NOTICE,DOCUMENT
     * - approvePermissions=NOTICE&approvePermissions=DOCUMENT
     *
     * Alias cũ BOTH vẫn được đọc là NOTICE,DOCUMENT.
     */
    private String approvePermission = PERMISSION_NONE;
    private List<String> approvePermissions = new ArrayList<>();

    /**
     * Permission for Room Booking feature.
     * NONE    = cannot add/edit/delete/tick index room booking
     * BOOKING = can manage room bookings
     */
    private String bookingPermission = BOOKING_NONE;

    /**
     * Checkbox permissions for menu/module actions.
     * Chỉ cho phép 3 quyền theo yêu cầu: NONE, NOTICE, DOCUMENT.
     * FE có thể gửi:
     * - modulePermission=NONE
     * - modulePermission=NOTICE,DOCUMENT
     * - modulePermissions=NOTICE&modulePermissions=DOCUMENT
     *
     * Alias cũ ALL được đọc là NOTICE,DOCUMENT. DEPARTMENT cũ sẽ bị bỏ qua.
     */
    private String modulePermission = PERMISSION_NONE;
    private List<String> modulePermissions = new ArrayList<>();

    private MultipartFile profileImage;

    private String normalizeNoticeDocumentPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PERMISSION_NONE;
        }

        String normalized = value.trim().toUpperCase();
        Set<String> permissions = new LinkedHashSet<>();

        for (String item : normalized.split(",")) {
            String cleanItem = item.trim();

            if (APPROVE_BOTH.equals(cleanItem) || "ALL".equals(cleanItem)) {
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

    private boolean hasPermission(String permissionValue, String target) {
        String normalized = normalizeNoticeDocumentPermission(permissionValue);

        if (PERMISSION_NONE.equals(normalized)) {
            return false;
        }

        return Arrays.asList(normalized.split(",")).contains(target);
    }

    private String normalizeBookingPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BOOKING_NONE;
        }

        String normalized = value.trim().toUpperCase();

        if (BOOKING_MANAGE.equals(normalized) || BOOKING_NONE.equals(normalized)) {
            return normalized;
        }

        return BOOKING_NONE;
    }

    public boolean canApproveNotice() {
        return hasPermission(getApprovePermission(), PERMISSION_NOTICE);
    }

    public boolean canApproveDocument() {
        return hasPermission(getApprovePermission(), PERMISSION_DOCUMENT);
    }

    public boolean canManageBooking() {
        return BOOKING_MANAGE.equals(normalizeBookingPermission(this.bookingPermission));
    }

    public boolean canManageNotice() {
        return hasPermission(getModulePermission(), PERMISSION_NOTICE);
    }

    public boolean canManageDocument() {
        return hasPermission(getModulePermission(), PERMISSION_DOCUMENT);
    }

    public boolean canManageDepartment() {
        return false;
    }

    // Getters and Setters
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

    public Boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(Boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getApprovePermission() {
        if (approvePermissions != null && !approvePermissions.isEmpty()) {
            return normalizeNoticeDocumentPermissions(approvePermissions);
        }

        return normalizeNoticeDocumentPermission(approvePermission);
    }

    public void setApprovePermission(String approvePermission) {
        this.approvePermission = normalizeNoticeDocumentPermission(approvePermission);
        this.approvePermissions = toPermissionList(this.approvePermission);
    }

    public List<String> getApprovePermissions() {
        return toPermissionList(getApprovePermission());
    }

    public void setApprovePermissions(List<String> approvePermissions) {
        this.approvePermissions = approvePermissions != null ? new ArrayList<>(approvePermissions) : new ArrayList<>();
        this.approvePermission = normalizeNoticeDocumentPermissions(this.approvePermissions);
    }

    public String getBookingPermission() {
        return normalizeBookingPermission(bookingPermission);
    }

    public void setBookingPermission(String bookingPermission) {
        this.bookingPermission = normalizeBookingPermission(bookingPermission);
    }

    public String getModulePermission() {
        if (modulePermissions != null && !modulePermissions.isEmpty()) {
            return normalizeNoticeDocumentPermissions(modulePermissions);
        }

        return normalizeNoticeDocumentPermission(modulePermission);
    }

    public void setModulePermission(String modulePermission) {
        this.modulePermission = normalizeNoticeDocumentPermission(modulePermission);
        this.modulePermissions = toPermissionList(this.modulePermission);
    }

    public List<String> getModulePermissions() {
        return toPermissionList(getModulePermission());
    }

    public void setModulePermissions(List<String> modulePermissions) {
        this.modulePermissions = modulePermissions != null ? new ArrayList<>(modulePermissions) : new ArrayList<>();
        this.modulePermission = normalizeNoticeDocumentPermissions(this.modulePermissions);
    }

    public MultipartFile getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(MultipartFile profileImage) {
        this.profileImage = profileImage;
    }
}
