package org.bsl.portal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class RoomBookingDto {

    private String id;
    private String title;
    private String roomId;
    private String roomName;
    private LocalDate checkInDate;
    private LocalTime checkInTime;
    private LocalDate checkOutDate;
    private LocalTime checkOutTime;
    private String peopleInCharge;
    private String basedLocation;
    private BigDecimal roomCharged;
    private Boolean showOnIndexRoom;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RoomBookingDto() {
    }

    // Constructor cũ: giữ lại để tránh lỗi compile nếu chỗ khác vẫn đang gọi constructor này.
    public RoomBookingDto(
            String id,
            String title,
            String roomId,
            String roomName,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            String peopleInCharge,
            String basedLocation,
            BigDecimal roomCharged,
            Boolean showOnIndexRoom,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                title,
                roomId,
                roomName,
                checkInDate,
                null,
                checkOutDate,
                null,
                peopleInCharge,
                basedLocation,
                roomCharged,
                showOnIndexRoom,
                createdBy,
                createdAt,
                updatedAt
        );
    }

    // Constructor mới: có checkInTime, checkOutTime.
    public RoomBookingDto(
            String id,
            String title,
            String roomId,
            String roomName,
            LocalDate checkInDate,
            LocalTime checkInTime,
            LocalDate checkOutDate,
            LocalTime checkOutTime,
            String peopleInCharge,
            String basedLocation,
            BigDecimal roomCharged,
            Boolean showOnIndexRoom,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.title = title;
        this.roomId = roomId;
        this.roomName = roomName;
        this.checkInDate = checkInDate;
        this.checkInTime = checkInTime;
        this.checkOutDate = checkOutDate;
        this.checkOutTime = checkOutTime;
        this.peopleInCharge = peopleInCharge;
        this.basedLocation = basedLocation;
        this.roomCharged = roomCharged;
        this.showOnIndexRoom = showOnIndexRoom;
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

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
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