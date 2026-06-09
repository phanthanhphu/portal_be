package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "room_booking_display_configs")
public class RoomBookingDisplayConfig {

    @Id
    private String id;

    // Tên list hiển thị, ví dụ: VIP Guests 10-12 Jun
    private String name;

    // Ngày bắt đầu lấy booking để hiển thị lên IndexRoom
    private LocalDate startDate;

    // Ngày kết thúc lấy booking để hiển thị lên IndexRoom
    private LocalDate endDate;

    // Chỉ cho phép 1 config enabled tại một thời điểm
    private boolean enabled;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RoomBookingDisplayConfig() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
