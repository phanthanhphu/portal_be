package org.bsl.portal.dto;

import org.bsl.portal.enums.FileType;

import java.time.LocalDateTime;

public class FormResponse {

    private String id;
    private String title;
    private String description;

    private String typeId;

    private FileType fileType;
    private String fileUrl;
    private String previewUrl;

    private String departmentId;
    private String departmentName;
    private String division;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FormResponse() {
    }

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
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
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