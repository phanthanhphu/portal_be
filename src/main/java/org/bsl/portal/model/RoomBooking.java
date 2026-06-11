package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Document(collection = "room_bookings")
public class RoomBooking {

    @Id
    private String id;

    // Có thể dùng làm tiêu đề booking hoặc tên khách nếu FE cũ đang dùng title.
    private String title;


    private String roomId;
    private LocalDate checkInDate;
    private LocalTime checkInTime;
    private LocalDate checkOutDate;
    private LocalTime checkOutTime;
    private String peopleInCharge;
    private String basedLocation;

    // VND amount. Store as whole number, no decimals.
    private BigDecimal roomCharged;

    // Tick checkbox to show this booking on Index Room screen.
    private Boolean showOnIndexRoom;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RoomBooking() {
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


    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public LocalTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public String getPeopleInCharge() {
        return peopleInCharge;
    }

    public void setPeopleInCharge(String peopleInCharge) {
        this.peopleInCharge = peopleInCharge;
    }

    public String getBasedLocation() {
        return basedLocation;
    }

    public void setBasedLocation(String basedLocation) {
        this.basedLocation = basedLocation;
    }

    public BigDecimal getRoomCharged() {
        return roomCharged;
    }

    public void setRoomCharged(BigDecimal roomCharged) {
        this.roomCharged = roomCharged;
    }

    public Boolean getShowOnIndexRoom() {
        return showOnIndexRoom;
    }

    public void setShowOnIndexRoom(Boolean showOnIndexRoom) {
        this.showOnIndexRoom = showOnIndexRoom;
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
