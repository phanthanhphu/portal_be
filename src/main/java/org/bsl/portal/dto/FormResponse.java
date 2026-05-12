package org.bsl.portal.dto;

import org.bsl.portal.enums.FileType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FormResponse {

    private String id;
    private String title;
    private String description;

    private String typeId;

    private FileType fileType;

    // Old fields: keep for old FE/API compatibility
    private String fileUrl;
    private String previewUrl;

    // New fields: support multiple files
    private List<String> fileUrls = new ArrayList<>();
    private List<String> previewUrls = new ArrayList<>();

    private String departmentId;
    private String departmentName;
    private String division;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FormResponse() {
    }

    // Old constructor: keep to avoid breaking existing service/query code
    public FormResponse(
            String id,
            String title,
            String description,
            String typeId,
            FileType fileType,
            String fileUrl,
            String previewUrl,
            String departmentId,
            String departmentName,
            String division,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.typeId = typeId;
        this.fileType = fileType;
        this.fileUrl = fileUrl;
        this.previewUrl = previewUrl;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.division = division;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        if (fileUrl != null && !fileUrl.trim().isEmpty()) {
            this.fileUrls.add(fileUrl.trim());
        }

        if (previewUrl != null && !previewUrl.trim().isEmpty()) {
            this.previewUrls.add(previewUrl.trim());
        }
    }

    // New constructor: use when service supports multiple files
    public FormResponse(
            String id,
            String title,
            String description,
            String typeId,
            FileType fileType,
            String fileUrl,
            String previewUrl,
            List<String> fileUrls,
            List<String> previewUrls,
            String departmentId,
            String departmentName,
            String division,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.typeId = typeId;
        this.fileType = fileType;
        this.fileUrls = cleanUrls(fileUrls);
        this.previewUrls = cleanUrls(previewUrls);
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.division = division;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

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

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getTypeId() {
        return typeId;
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

    public String getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getDivision() {
        return division;
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

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId;
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

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
