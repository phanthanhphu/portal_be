package org.bsl.portal.controller;

import org.bsl.portal.common.socket.AppSocketPublisher;
import org.bsl.portal.dto.RoomBookingDto;
import org.bsl.portal.model.RoomBooking;
import org.bsl.portal.service.RoomBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/room-bookings")
public class RoomBookingController {

    @Autowired
    private RoomBookingService service;

    @Autowired
    private AppSocketPublisher appSocketPublisher;

    // ==================== CREATE ROOM BOOKING ====================
    @PostMapping
    public ResponseEntity<?> create(@RequestBody RoomBooking booking) {
        try {
            RoomBooking created = service.create(booking);

            appSocketPublisher.roomBookingChanged("CREATED", created.getId());

            return ResponseEntity.ok(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Create room booking failed: " + e.getMessage()));
        }
    }

    // ==================== UPDATE ROOM BOOKING ====================
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody RoomBooking booking
    ) {
        try {
            RoomBooking updated = service.update(id, booking);

            appSocketPublisher.roomBookingChanged("UPDATED", updated.getId());

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Update room booking failed: " + e.getMessage()));
        }
    }

    // ==================== DELETE ROOM BOOKING ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            service.delete(id);

            appSocketPublisher.roomBookingChanged("DELETED", id);

            return ResponseEntity.ok(message("Deleted successfully"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Delete room booking failed: " + e.getMessage()));
        }
    }

    // ==================== GET ALL ROOM BOOKINGS ====================
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch room bookings failed: " + e.getMessage()));
        }
    }

    // ==================== GET INDEX ROOM BOOKINGS ====================
    // Chỉ trả những booking đã tick Show on Index Room.
    @GetMapping("/index-room")
    public ResponseEntity<?> getIndexRoomBookings() {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("content", service.getIndexRoomBookings());
            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch index room bookings failed: " + e.getMessage()));
        }
    }

    // ==================== GET ROOM BOOKING BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        try {
            RoomBookingDto booking = service.getById(id);
            return ResponseEntity.ok(booking);

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch room booking failed: " + e.getMessage()));
        }
    }

    // ==================== SEARCH ROOM BOOKINGS WITH PAGINATION ====================
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Page<RoomBookingDto> result = service.search(name, roomId, page, size);

            Map<String, Object> body = new HashMap<>();
            body.put("content", result.getContent());
            body.put("page", result.getNumber());
            body.put("size", result.getSize());
            body.put("totalElements", result.getTotalElements());
            body.put("totalPages", result.getTotalPages());
            body.put("hasNext", result.hasNext());
            body.put("hasPrevious", result.hasPrevious());
            body.put("first", result.isFirst());
            body.put("last", result.isLast());

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Search room bookings failed: " + e.getMessage()));
        }
    }

    // ==================== GET BOOKINGS BY ROOM ID ====================
    @GetMapping("/by-room/{roomId}")
    public ResponseEntity<?> getByRoomId(@PathVariable String roomId) {
        try {
            return ResponseEntity.ok(service.getByRoomId(roomId));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch room bookings by room failed: " + e.getMessage()));
        }
    }

    // ==================== CHECKBOX SHOW ON INDEX ROOM ====================
    @PatchMapping("/{id}/index-room-display")
    public ResponseEntity<?> updateIndexRoomDisplay(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean enabled
    ) {
        try {
            RoomBookingDto updated = service.updateIndexRoomDisplay(id, enabled);

            appSocketPublisher.roomBookingChanged(
                    enabled ? "INDEX_ROOM_ENABLED" : "INDEX_ROOM_DISABLED",
                    updated.getId()
            );

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Update index room display failed: " + e.getMessage()));
        }
    }

    private Map<String, String> message(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
