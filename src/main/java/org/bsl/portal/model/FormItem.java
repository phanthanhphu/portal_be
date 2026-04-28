package org.bsl.portal.model;

import org.bsl.portal.enums.FileType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "forms")
public class FormItem {

    @Id
    private String id;

    private String departmentId; // liên kết phòng ban

    private String typeId; // liên kết loại document: Form, Quy định, Thông báo...

    private String title;
    private String description;
    private FileType fileType;

    private String fileUrl;
    private String previewUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FormItem() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

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

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
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
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}