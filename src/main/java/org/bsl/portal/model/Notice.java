package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "notices")
public class Notice {

    @Id
    private String id;

    // tiêu đề thông báo
    private String title;

    // nội dung
    private String content;

    // file upload (pdf, image, doc...)
    private String fileUrl;

    // ghim thông báo
    private Boolean pinned;

    // user tạo notice
    @Indexed
    private String userId;

    // phòng ban
    @Indexed
    private String departmentId;

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
    }

    public Notice(String title, String content, String fileUrl, Boolean pinned, String userId, String departmentId) {
        this.title = title;
        this.content = content;
        this.fileUrl = fileUrl;
        this.pinned = pinned;
        this.userId = userId;
        this.departmentId = departmentId;

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
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