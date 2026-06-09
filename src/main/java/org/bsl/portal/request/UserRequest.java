package org.bsl.portal.request;

import org.springframework.web.multipart.MultipartFile;

public class UserRequest {
    public static final String APPROVE_NONE = "NONE";
    public static final String APPROVE_NOTICE = "NOTICE";
    public static final String APPROVE_DOCUMENT = "DOCUMENT";
    public static final String APPROVE_BOTH = "BOTH";

    public static final String BOOKING_NONE = "NONE";
    public static final String BOOKING_MANAGE = "BOOKING";

    private String username;
    private String email;
    private String password;
    private String address;
    private String phone;
    private String role;
    private Boolean isEnabled;
    private String departmentId;

    /**
     * Permission for approval features.
     * NONE     = cannot approve anything
     * NOTICE   = can approve notices only
     * DOCUMENT = can approve documents only
     * BOTH     = can approve both notices and documents
     */
    private String approvePermission = APPROVE_NONE;

    /**
     * Permission for Room Booking feature.
     * NONE    = cannot add/edit/delete/tick index room booking
     * BOOKING = can manage room bookings
     */
    private String bookingPermission = BOOKING_NONE;

    private MultipartFile profileImage;

    private String normalizeApprovePermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return APPROVE_NONE;
        }

        String normalized = value.trim().toUpperCase();

        if (APPROVE_NOTICE.equals(normalized)
                || APPROVE_DOCUMENT.equals(normalized)
                || APPROVE_BOTH.equals(normalized)
                || APPROVE_NONE.equals(normalized)) {
            return normalized;
        }

        return APPROVE_NONE;
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
        String permission = normalizeApprovePermission(this.approvePermission);
        return APPROVE_NOTICE.equals(permission) || APPROVE_BOTH.equals(permission);
    }

    public boolean canApproveDocument() {
        String permission = normalizeApprovePermission(this.approvePermission);
        return APPROVE_DOCUMENT.equals(permission) || APPROVE_BOTH.equals(permission);
    }

    public boolean canManageBooking() {
        return BOOKING_MANAGE.equals(normalizeBookingPermission(this.bookingPermission));
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
        return normalizeApprovePermission(approvePermission);
    }

    public void setApprovePermission(String approvePermission) {
        this.approvePermission = normalizeApprovePermission(approvePermission);
    }

    public String getBookingPermission() {
        return normalizeBookingPermission(bookingPermission);
    }

    public void setBookingPermission(String bookingPermission) {
        this.bookingPermission = normalizeBookingPermission(bookingPermission);
    }

    public MultipartFile getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(MultipartFile profileImage) {
        this.profileImage = profileImage;
    }
}
