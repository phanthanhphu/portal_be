package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "notices")
public class Notice {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    private String id;

    // tiêu đề thông báo
    private String title;

    // nội dung
    private String content;

    // file upload cũ: giữ lại để không lỗi dữ liệu/API/UI cũ
    private String fileUrl;

    // preview file cũ: giữ lại để đồng bộ với nhiều file
    private String previewUrl;

    // danh sách file mới: hỗ trợ nhiều file
    private List<String> fileUrls = new ArrayList<>();

    // danh sách preview mới
    private List<String> previewUrls = new ArrayList<>();

    // ghim thông báo
    private Boolean pinned;

    // user tạo notice
    @Indexed
    private String userId;

    // phòng ban
    @Indexed
    private String departmentId;

    // trạng thái duyệt bài: PENDING, APPROVED, REJECTED
    // Mặc định APPROVED để dữ liệu cũ chưa có status vẫn hiển thị bình thường.
    @Indexed
    private String status = STATUS_APPROVED;

    // admin duyệt bài
    private String approvedBy;

    // thời gian duyệt bài
    @Indexed
    private LocalDateTime approvedAt;

    // admin từ chối bài
    private String rejectedBy;

    // thời gian từ chối bài
    @Indexed
    private LocalDateTime rejectedAt;

    // lý do từ chối bài
    private String rejectReason;

    // ngày tạo
    @Indexed
    private LocalDateTime createdAt;

    // ngày cập nhật
    @Indexed
    private LocalDateTime updatedAt;

    public Notice() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.pinned = false;
        this.status = STATUS_APPROVED;
    }

    // Constructor cũ: giữ lại để không làm lỗi code đang gọi hiện tại
    public Notice(String title, String content, String fileUrl, Boolean pinned, String userId, String departmentId) {
        this.title = title;
        this.content = content;
        this.fileUrl = fileUrl;
        this.previewUrl = fileUrl;
        this.pinned = pinned;
        this.userId = userId;
        this.departmentId = departmentId;
        this.status = STATUS_APPROVED;

        if (fileUrl != null && !fileUrl.trim().isEmpty()) {
            this.fileUrls.add(fileUrl.trim());
            this.previewUrls.add(fileUrl.trim());
        }

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Constructor mới: dùng khi tạo notice có nhiều file
    public Notice(
            String title,
            String content,
            String fileUrl,
            String previewUrl,
            List<String> fileUrls,
            List<String> previewUrls,
            Boolean pinned,
            String userId,
            String departmentId
    ) {
        this.title = title;
        this.content = content;
        this.fileUrls = cleanUrls(fileUrls);
        this.previewUrls = cleanUrls(previewUrls);
        this.pinned = pinned;
        this.userId = userId;
        this.departmentId = departmentId;
        this.status = STATUS_APPROVED;

        if (!this.fileUrls.isEmpty()) {
            this.fileUrl = this.fileUrls.get(0);
        } else {
            this.fileUrl = fileUrl;

            if (fileUrl != null && !fileUrl.trim().isEmpty()) {
                this.fileUrls.add(fileUrl.trim());
            }
        }

        if (!this.previewUrls.isEmpty()) {
            this.previewUrl = this.previewUrls.get(0);
        } else {
            this.previewUrl = previewUrl != null ? previewUrl : this.fileUrl;

            if (this.previewUrl != null && !this.previewUrl.trim().isEmpty()) {
                this.previewUrls.add(this.previewUrl.trim());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private List<String> cleanUrls(List<String> urls) {
        List<String> result = new ArrayList<>();

        if (urls == null) {
            return result;
        }

        for (String url : urls) {
            if (url != null && !url.trim().isEmpty() && !result.contains(url.trim())) {
                result.add(url.trim());
            }
        }

        return result;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.trim().isEmpty()) {
            return STATUS_APPROVED;
        }

        String cleanStatus = value.trim().toUpperCase();

        if (STATUS_PENDING.equals(cleanStatus)
                || STATUS_APPROVED.equals(cleanStatus)
                || STATUS_REJECTED.equals(cleanStatus)) {
            return cleanStatus;
        }

        return STATUS_APPROVED;
    }

    private void syncSingleFileFromList() {
        if (fileUrls != null && !fileUrls.isEmpty()) {
            this.fileUrl = fileUrls.get(0);
        } else {
            this.fileUrl = null;
        }

        if (previewUrls != null && !previewUrls.isEmpty()) {
            this.previewUrl = previewUrls.get(0);
        } else {
            this.previewUrl = this.fileUrl;
        }
    }

    private void syncListFromSingleFile() {
        if ((fileUrls == null || fileUrls.isEmpty())
                && fileUrl != null
                && !fileUrl.trim().isEmpty()) {
            this.fileUrls = new ArrayList<>();
            this.fileUrls.add(fileUrl.trim());
        }

        if ((previewUrls == null || previewUrls.isEmpty())
                && previewUrl != null
                && !previewUrl.trim().isEmpty()) {
            this.previewUrls = new ArrayList<>();
            this.previewUrls.add(previewUrl.trim());
        }

        if ((previewUrls == null || previewUrls.isEmpty())
                && fileUrl != null
                && !fileUrl.trim().isEmpty()) {
            this.previewUrls = new ArrayList<>();
            this.previewUrls.add(fileUrl.trim());
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;

        if (this.fileUrls == null) {
            this.fileUrls = new ArrayList<>();
        }

        if (this.fileUrls.isEmpty() && fileUrl != null && !fileUrl.trim().isEmpty()) {
            this.fileUrls.add(fileUrl.trim());
        }

        if (this.previewUrl == null || this.previewUrl.trim().isEmpty()) {
            this.previewUrl = fileUrl;
        }

        syncListFromSingleFile();
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;

        if (this.previewUrls == null) {
            this.previewUrls = new ArrayList<>();
        }

        if (this.previewUrls.isEmpty() && previewUrl != null && !previewUrl.trim().isEmpty()) {
            this.previewUrls.add(previewUrl.trim());
        }

        syncListFromSingleFile();
    }

    public List<String> getFileUrls() {
        syncListFromSingleFile();
        return fileUrls;
    }

    public void setFileUrls(List<String> fileUrls) {
        this.fileUrls = cleanUrls(fileUrls);
        syncSingleFileFromList();
    }

    public List<String> getPreviewUrls() {
        syncListFromSingleFile();
        return previewUrls;
    }

    public void setPreviewUrls(List<String> previewUrls) {
        this.previewUrls = cleanUrls(previewUrls);
        syncSingleFileFromList();
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getStatus() {
        this.status = normalizeStatus(this.status);
        return status;
    }

    public void setStatus(String status) {
        this.status = normalizeStatus(status);
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
