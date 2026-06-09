package org.bsl.portal.controller;

import org.bsl.portal.dto.RoomBookingDisplayConfigDto;
import org.bsl.portal.dto.RoomBookingDto;
import org.bsl.portal.model.RoomBookingDisplayConfig;
import org.bsl.portal.service.RoomBookingDisplayConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/room-booking-display-configs")
public class RoomBookingDisplayConfigController {

    @Autowired
    private RoomBookingDisplayConfigService service;

    // ==================== CREATE DISPLAY CONFIG ====================
    @PostMapping
    public ResponseEntity<?> create(@RequestBody RoomBookingDisplayConfig config) {
        try {
            return ResponseEntity.ok(service.create(config));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Create display config failed: " + e.getMessage()));
        }
    }

    // ==================== UPDATE DISPLAY CONFIG ====================
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody RoomBookingDisplayConfig config
    ) {
        try {
            return ResponseEntity.ok(service.update(id, config));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Update display config failed: " + e.getMessage()));
        }
    }

    // ==================== DELETE DISPLAY CONFIG ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            service.delete(id);
            return ResponseEntity.ok(message("Deleted successfully"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Delete display config failed: " + e.getMessage()));
        }
    }

    // ==================== GET ALL DISPLAY CONFIGS ====================
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch display configs failed: " + e.getMessage()));
        }
    }

    // ==================== GET DISPLAY CONFIG BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.getById(id));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch display config failed: " + e.getMessage()));
        }
    }

    // ==================== SEARCH DISPLAY CONFIGS ====================
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Page<RoomBookingDisplayConfigDto> result = service.search(name, page, size);

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
                    .body(message("Search display configs failed: " + e.getMessage()));
        }
    }

    // ==================== ENABLE ONLY ONE CONFIG ====================
    @PatchMapping("/{id}/enable")
    public ResponseEntity<?> enable(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.enable(id));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Enable display config failed: " + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/disable")
    public ResponseEntity<?> disable(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.disable(id));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(message(e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(message(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Disable display config failed: " + e.getMessage()));
        }
    }

    // ==================== GET ACTIVE CONFIG ====================
    @GetMapping("/active")
    public ResponseEntity<?> getActiveConfig() {
        try {
            Optional<RoomBookingDisplayConfigDto> active = service.getActiveConfig();

            Map<String, Object> body = new HashMap<>();
            body.put("active", active.isPresent());
            body.put("config", active.orElse(null));

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch active display config failed: " + e.getMessage()));
        }
    }

    // ==================== GET BOOKINGS FOR INDEX ROOM ====================
    @GetMapping("/active-bookings")
    public ResponseEntity<?> getActiveBookings() {
        try {
            Optional<RoomBookingDisplayConfigDto> active = service.getActiveConfig();
            List<RoomBookingDto> bookings = service.getActiveBookingsForIndexRoom();

            Map<String, Object> body = new HashMap<>();
            body.put("active", active.isPresent());
            body.put("config", active.orElse(null));
            body.put("content", bookings);
            body.put("totalElements", bookings.size());

            return ResponseEntity.ok(body);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(message("Fetch active bookings failed: " + e.getMessage()));
        }
    }

    private Map<String, String> message(String message) {
        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        return body;
    }
}
