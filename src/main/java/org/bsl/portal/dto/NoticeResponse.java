package org.bsl.portal.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class NoticeResponse {

    private String id;
    private String title;
    private String content;

    // Field cũ: giữ lại để không làm lỗi FE/API cũ
    private String fileUrl;

    // Field cũ/mới: preview file đầu tiên
    private String previewUrl;

    // Field mới: hỗ trợ nhiều file
    private List<String> fileUrls = new ArrayList<>();

    // Field mới: hỗ trợ nhiều preview
    private List<String> previewUrls = new ArrayList<>();

    private Boolean pinned;
    private String userId;
    private String departmentId;
    private String departmentName;
    private String division;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public NoticeResponse() {
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

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
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
