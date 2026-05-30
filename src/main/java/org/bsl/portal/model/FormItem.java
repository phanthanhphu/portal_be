package org.bsl.portal.model;

import org.bsl.portal.enums.FileType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "forms")
public class FormItem {

    @Id
    private String id;

    private String departmentId; // liên kết phòng ban

    private String typeId; // liên kết loại document: Form, Quy định, Thông báo...

    private String title;
    private String description;
    private FileType fileType;

    // Field cũ: giữ lại để không làm lỗi dữ liệu/API/FE cũ
    private String fileUrl;
    private String previewUrl;

    // Field mới: hỗ trợ nhiều file, tối đa 5 file sẽ check ở Controller
    private List<String> fileUrls = new ArrayList<>();
    private List<String> previewUrls = new ArrayList<>();

    /**
     * Approval workflow for Document/Form approval page.
     *
     * PENDING  : tài liệu mới tạo, chờ duyệt
     * APPROVED : tài liệu đã duyệt, được hiển thị/sử dụng chính thức
     * REJECTED : tài liệu bị từ chối, có thể kèm lý do
     *
     * Default APPROVED giúp dữ liệu cũ chưa có status vẫn hoạt động bình thường.
     * Khi tạo tài liệu mới, Controller có thể setStatus("PENDING").
     */
    private String status = "APPROVED";
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String rejectedBy;
    private LocalDateTime rejectedAt;
    private String rejectReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FormItem() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Constructor cũ: giữ lại để không làm lỗi code hiện tại
    public FormItem(
            String departmentId,
            String typeId,
            String title,
            String description,
            FileType fileType,
            String fileUrl,
            String previewUrl
    ) {
        this.departmentId = departmentId;
        this.typeId = typeId;
        this.title = title;
        this.description = description;
        this.fileType = fileType;
        this.fileUrl = fileUrl;
        this.previewUrl = previewUrl;

        if (fileUrl != null && !fileUrl.trim().isEmpty()) {
            this.fileUrls.add(fileUrl.trim());
        }

        if (previewUrl != null && !previewUrl.trim().isEmpty()) {
            this.previewUrls.add(previewUrl.trim());
        }

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Constructor mới: dùng khi cần tạo form có nhiều file
    public FormItem(
            String departmentId,
            String typeId,
            String title,
            String description,
            FileType fileType,
            String fileUrl,
            String previewUrl,
            List<String> fileUrls,
            List<String> previewUrls
    ) {
        this.departmentId = departmentId;
        this.typeId = typeId;
        this.title = title;
        this.description = description;
        this.fileType = fileType;
        this.fileUrls = cleanUrls(fileUrls);
        this.previewUrls = cleanUrls(previewUrls);

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
            this.previewUrl = previewUrl;

            if (previewUrl != null && !previewUrl.trim().isEmpty()) {
                this.previewUrls.add(previewUrl.trim());
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

    private void syncSingleFileFromList() {
        if (fileUrls != null && !fileUrls.isEmpty()) {
            this.fileUrl = fileUrls.get(0);
        } else {
            this.fileUrl = null;
        }

        if (previewUrls != null && !previewUrls.isEmpty()) {
            this.previewUrl = previewUrls.get(0);
        } else {
            this.previewUrl = null;
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
    }

    private String normalizeStatus(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "APPROVED";
        }

        String normalized = value.trim().toUpperCase();

        if ("PENDING".equals(normalized)
                || "APPROVED".equals(normalized)
                || "REJECTED".equals(normalized)) {
            return normalized;
        }

        return "APPROVED";
    }

    public String getId() {
        return id;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public String getTypeId() {
        return typeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public FileType getFileType() {
        return fileType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public List<String> getFileUrls() {
        syncListFromSingleFile();
        return fileUrls;
    }

    public List<String> getPreviewUrls() {
        syncListFromSingleFile();
        return previewUrls;
    }

    public String getStatus() {
        return normalizeStatus(status);
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;

        if (this.fileUrls == null) {
            this.fileUrls = new ArrayList<>();
        }

        if (this.fileUrls.isEmpty() && fileUrl != null && !fileUrl.trim().isEmpty()) {
            this.fileUrls.add(fileUrl.trim());
        }
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;

        if (this.previewUrls == null) {
            this.previewUrls = new ArrayList<>();
        }

        if (this.previewUrls.isEmpty() && previewUrl != null && !previewUrl.trim().isEmpty()) {
            this.previewUrls.add(previewUrl.trim());
        }
    }

    public void setFileUrls(List<String> fileUrls) {
        this.fileUrls = cleanUrls(fileUrls);
        syncSingleFileFromList();
    }

    public void setPreviewUrls(List<String> previewUrls) {
        this.previewUrls = cleanUrls(previewUrls);
        syncSingleFileFromList();
    }

    public void setStatus(String status) {
        this.status = normalizeStatus(status);
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
