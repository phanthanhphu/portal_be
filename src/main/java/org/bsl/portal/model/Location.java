package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "locations")
public class Location {

    @Id
    private String id;

    // Tên location / based location
    private String location;

    // User ID người tạo
    private String userIdCreate;

    // Ngày tạo
    private LocalDateTime createdAt;

    // Ngày cập nhật
    private LocalDateTime updatedAt;

    public Location() {
    }

    public Location(String id, String location, String userIdCreate, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.location = location;
        this.userIdCreate = userIdCreate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUserIdCreate() {
        return userIdCreate;
    }

    public void setUserIdCreate(String userIdCreate) {
        this.userIdCreate = userIdCreate;
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
