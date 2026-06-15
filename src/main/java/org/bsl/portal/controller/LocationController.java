package org.bsl.portal.controller;

import org.bsl.portal.dto.LocationRequest;
import org.bsl.portal.model.Location;
import org.bsl.portal.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    @Autowired
    private LocationService service;

    // ==================== CREATE LOCATION ====================
    @PostMapping
    public ResponseEntity<?> create(@RequestBody LocationRequest request) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Request body is required"));
            }

            /*
             * Field API/DB vẫn tên là userIdCreate để không phá cấu trúc cũ.
             * Nhưng giá trị lưu vào sẽ là EMAIL người tạo, không phải user ID/username.
             */
            String createdByEmail = resolveCreatedByEmail(request);

            Location created = service.create(
                    request.getLocation(),
                    createdByEmail
            );

            return ResponseEntity.ok(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Create location failed: " + e.getMessage()));
        }
    }

    // ==================== UPDATE LOCATION ====================
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody LocationRequest request
    ) {
        try {
            if (request == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Request body is required"));
            }

            Location updated = service.update(id, request.getLocation());

            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Location not found"));
            }

            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update location failed: " + e.getMessage()));
        }
    }

    // ==================== DELETE LOCATION ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            service.delete(id);

            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Delete location failed: " + e.getMessage()));
        }
    }

    // ==================== GET ALL LOCATIONS ====================
    @GetMapping("/all")
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch locations failed: " + e.getMessage()));
        }
    }

    // ==================== GET LOCATION OPTIONS FOR DROPDOWN ====================
    @GetMapping("/options")
    public ResponseEntity<?> getOptions() {
        try {
            List<Map<String, String>> options = service.getAll().stream()
                    .map(item -> {
                        Map<String, String> option = new HashMap<>();
                        option.put("id", item.getId());
                        option.put("location", item.getLocation());
                        return option;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(options);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch location options failed: " + e.getMessage()));
        }
    }

    // ==================== GET BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        try {
            Location item = service.getById(id);

            if (item == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Location not found"));
            }

            return ResponseEntity.ok(item);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch location failed: " + e.getMessage()));
        }
    }

    // ==================== SEARCH / PAGING ====================
    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            int safePage = Math.max(page, 0);
            int safeSize = Math.max(size, 1);

            Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));

            Pageable pageable = PageRequest.of(safePage, safeSize, sort);

            Page<Location> result = service.search(keyword, pageable);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Search locations failed: " + e.getMessage()));
        }
    }

    /*
     * Chỉ lưu EMAIL vào Created By.
     *
     * Ưu tiên:
     * 1. Email FE gửi qua request.userIdCreate
     * 2. Email từ Authentication.getName()
     * 3. SYSTEM
     *
     * Không lưu Mongo/User ID và không lưu username không có @.
     */
    private String resolveCreatedByEmail(LocationRequest request) {
        String valueFromRequest = trimToNull(request.getUserIdCreate());

        if (isEmail(valueFromRequest) && !isMongoObjectId(valueFromRequest)) {
            return valueFromRequest;
        }

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                String name = trimToNull(authentication.getName());

                if (isEmail(name) && !isMongoObjectId(name)) {
                    return name;
                }
            }
        } catch (Exception ignored) {
            // fallback below
        }

        return "SYSTEM";
    }

    private boolean isEmail(String value) {
        if (value == null) {
            return false;
        }

        String normalized = value.trim();

        return normalized.contains("@")
                && normalized.indexOf("@") > 0
                && normalized.indexOf("@") < normalized.length() - 1;
    }

    private boolean isMongoObjectId(String value) {
        return value != null && value.matches("^[0-9a-fA-F]{24}$");
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
