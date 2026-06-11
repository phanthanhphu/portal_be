package org.bsl.portal.service;

import org.bsl.portal.model.Room;
import org.bsl.portal.repository.RoomBookingRepository;
import org.bsl.portal.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class RoomService {

    @Autowired
    private RoomRepository repository;

    @Autowired
    private RoomBookingRepository roomBookingRepository;

    // ==================== CREATE ROOM ====================
    public Room create(Room room) {
        validateRoom(room);

        String roomName = room.getRoomName().trim();

        if (repository.existsByRoomNameIgnoreCase(roomName)) {
            throw new IllegalArgumentException("Room name already exists");
        }

        room.setId(null);
        room.setRoomName(roomName);
        room.setCreatedBy(resolveCreatedBy(room));
        room.setCreatedAt(LocalDateTime.now());
        room.setUpdatedAt(LocalDateTime.now());

        return repository.save(room);
    }

    // ==================== UPDATE ROOM ====================
    public Room update(String id, Room room) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room id is required");
        }

        Room existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Room not found"));

        validateRoom(room);

        String roomName = room.getRoomName().trim();

        if (repository.existsByRoomNameIgnoreCase(roomName)
                && !roomName.equalsIgnoreCase(existing.getRoomName())) {
            throw new IllegalArgumentException("Room name already exists");
        }

        existing.setRoomName(roomName);
        existing.setUpdatedAt(LocalDateTime.now());

        // Không cập nhật createdBy, createdAt khi edit để giữ lịch sử người tạo.
        return repository.save(existing);
    }

    // ==================== DELETE ROOM ====================
    public void delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room id is required");
        }

        String roomId = id.trim();

        Room existing = repository.findById(roomId)
                .orElseThrow(() -> new NoSuchElementException("Room not found"));

        boolean roomIsUsed = roomBookingRepository.existsByRoomId(roomId);

        if (roomIsUsed) {
            throw new IllegalArgumentException(
                    "Cannot delete this room because it is already used in room bookings"
            );
        }

        repository.delete(existing);
    }

    // ==================== GET ALL ROOMS ====================
    public List<Room> getAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    // ==================== GET ROOM BY ID ====================
    public Room getById(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Room id is required");
        }

        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Room not found"));
    }

    // ==================== SEARCH ROOM BY NAME WITH PAGINATION ====================
    public Page<Room> searchByName(String name, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : Math.min(size, 100);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        if (name == null || name.trim().isEmpty()) {
            return repository.findAll(pageable);
        }

        return repository.findByRoomNameContainingIgnoreCase(name.trim(), pageable);
    }

    // ==================== ROOM OPTIONS ====================
    // Dùng cho dropdown chọn phòng trong RoomBooking.
    public List<Room> getRoomOptions() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "roomName"));
    }

    // Giữ endpoint cũ /available nếu FE trước đó đã gọi, nhưng hiện không còn field active.
    public List<Room> getAvailableRooms() {
        return getRoomOptions();
    }

    // ==================== VALIDATE ====================
    private void validateRoom(Room room) {
        if (room == null) {
            throw new IllegalArgumentException("Room data is required");
        }

        if (room.getRoomName() == null || room.getRoomName().trim().isEmpty()) {
            throw new IllegalArgumentException("Room name is required");
        }
    }

    private String resolveCreatedBy(Room room) {
        if (room.getCreatedBy() != null && !room.getCreatedBy().trim().isEmpty()) {
            return room.getCreatedBy().trim();
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                String name = authentication.getName();

                if (name != null && !name.trim().isEmpty() && !"anonymousUser".equalsIgnoreCase(name)) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
            // Nếu không lấy được user từ SecurityContext thì dùng SYSTEM.
        }

        return "SYSTEM";
    }
}
