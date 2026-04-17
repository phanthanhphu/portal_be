package org.bsl.portal.dto;

import org.bsl.portal.enums.FileType;

import java.time.LocalDateTime;

public class FormResponse {

    private String id;
    private String title;
    private String description;

    private FileType fileType;

    private String fileUrl;
    private String previewUrl;

    private String departmentId;
    private String departmentName;
    private String division;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== Constructor rỗng =====
    public FormResponse() {
    }

    // ===== Constructor đầy đủ =====
    public FormResponse(
            String id,
            String title,
            String description,
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
        this.fileType = fileType;
        this.fileUrl = fileUrl;
        this.previewUrl = previewUrl;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.division = division;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ===== Getter & Setter =====

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
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