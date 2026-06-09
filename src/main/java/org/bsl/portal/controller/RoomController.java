package org.bsl.portal.controller;

import org.bsl.portal.model.Room;
import org.bsl.portal.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomService service;

    // ==================== CREATE ROOM ====================
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Room room) {
        try {
            Room created = service.create(room);
            return ResponseEntity.ok(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Create room failed: " + e.getMessage()));
        }
    }

    // ==================== UPDATE ROOM ====================
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody Room room
    ) {
        try {
            Room updated = service.update(id, room);
            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update room failed: " + e.getMessage()));
        }
    }

    // ==================== DELETE ROOM ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            service.delete(id);
            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Delete room failed: " + e.getMessage()));
        }
    }

    // ==================== GET ALL ROOMS ====================
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch rooms failed: " + e.getMessage()));
        }
    }

    // ==================== GET ROOM BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        try {
            Room room = service.getById(id);
            return ResponseEntity.ok(room);

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch room failed: " + e.getMessage()));
        }
    }

    // ==================== SEARCH ROOM BY NAME WITH PAGINATION ====================
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Page<Room> result = service.searchByName(name, page, size);

            return ResponseEntity.ok(Map.of(
                    "content", result.getContent(),
                    "page", result.getNumber(),
                    "size", result.getSize(),
                    "totalElements", result.getTotalElements(),
                    "totalPages", result.getTotalPages(),
                    "hasNext", result.hasNext(),
                    "hasPrevious", result.hasPrevious(),
                    "first", result.isFirst(),
                    "last", result.isLast()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Search rooms failed: " + e.getMessage()));
        }
    }

    // ==================== GET ROOM OPTIONS ====================
    // Dùng cho dropdown chọn phòng khi tạo RoomBooking.
    @GetMapping("/options")
    public ResponseEntity<?> getRoomOptions() {
        try {
            return ResponseEntity.ok(service.getRoomOptions());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch room options failed: " + e.getMessage()));
        }
    }

    // ==================== GET AVAILABLE ROOMS ====================
    // Giữ lại để FE cũ không bị lỗi; hiện tại trả về toàn bộ danh mục phòng.
    @GetMapping("/available")
    public ResponseEntity<?> getAvailableRooms() {
        try {
            return ResponseEntity.ok(service.getAvailableRooms());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch available rooms failed: " + e.getMessage()));
        }
    }
}
