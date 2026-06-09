package org.bsl.portal.dto;

import java.time.LocalDateTime;

public class RoomDto {

    private String id;
    private String roomName;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RoomDto() {
    }

    public RoomDto(String id, String roomName, String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.roomName = roomName;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
