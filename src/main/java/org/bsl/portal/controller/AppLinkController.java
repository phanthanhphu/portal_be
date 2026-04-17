package org.bsl.portal.controller;

import org.bsl.portal.model.AppLink;
import org.bsl.portal.service.AppLinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/app-links")
public class AppLinkController {

    @Autowired
    private AppLinkService appLinkService;

    private static final String UPLOAD_DIR = "uploads/";

    private static final Pattern URL_PATTERN =
            Pattern.compile("^(http|https)://.*$", Pattern.CASE_INSENSITIVE);

    // ===============================
    // SAVE IMAGE
    // ===============================
    private String saveImage(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            return null;
        }

        String contentType = file.getContentType();

        if (!java.util.Arrays.asList("image/jpeg", "image/png", "image/gif").contains(contentType)) {
            throw new IOException("Only JPEG, PNG, GIF allowed");
        }

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

        Path path = Paths.get(UPLOAD_DIR + fileName);

        Files.createDirectories(path.getParent());

        file.transferTo(path);

        return "/uploads/" + fileName;
    }

    // ===============================
    // DELETE IMAGE
    // ===============================
    private void deleteImage(String imageUrl) {

        try {

            if (imageUrl == null) {
                return;
            }

            String fileName = imageUrl.replace("/uploads/", "");

            Path path = Paths.get(UPLOAD_DIR + fileName);

            Files.deleteIfExists(path);

        } catch (Exception ignored) {
        }
    }

    // ===============================
    // CREATE
    // ===============================
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String name,
            @RequestParam String url,
            @RequestParam(required = false) String desc,
            @RequestPart(required = false) MultipartFile image
    ) {

        try {

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Name is required"));
            }

            if (appLinkService.existsByName(name)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "AppLink name already exists"));
            }

            if (!URL_PATTERN.matcher(url).matches()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "URL must start with http:// or https://"));
            }

            String imageUrl = saveImage(image);

            AppLink link = appLinkService.create(name, url, imageUrl, desc);

            return ResponseEntity.ok(link);

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create AppLink: " + e.getMessage()));
        }
    }

    // ===============================
    // UPDATE
    // ===============================
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String name,
            @RequestParam String url,
            @RequestParam(required = false) String desc,
            @RequestPart(required = false) MultipartFile image
    ) {

        try {

            AppLink existing = appLinkService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "AppLink not found"));
            }

            if (!URL_PATTERN.matcher(url).matches()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid URL format"));
            }

            String imageUrl = existing.getIcon();

            if (image != null && !image.isEmpty()) {

                deleteImage(existing.getIcon());

                imageUrl = saveImage(image);
            }

            AppLink updated = appLinkService.update(id, name, url, imageUrl, desc);

            return ResponseEntity.ok(updated);

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update AppLink: " + e.getMessage()));
        }
    }

    // ===============================
    // DELETE
    // ===============================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {

        try {

            AppLink existing = appLinkService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "AppLink not found"));
            }

            deleteImage(existing.getIcon());

            appLinkService.delete(id);

            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete AppLink: " + e.getMessage()));
        }
    }

    // ===============================
    // GET BY ID
    // ===============================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {

        AppLink link = appLinkService.getById(id);

        if (link == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "AppLink not found"));
        }

        return ResponseEntity.ok(link);
    }

    // ===============================
    // GET ALL PAGED
    // ===============================
    @GetMapping
    public ResponseEntity<?> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        Page<AppLink> result = appLinkService.getAllPaged(page, size);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<AppLink>> searchPaged(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String desc,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AppLink> result = appLinkService.getAllPagedWithSearch(name, desc, pageable);

        return ResponseEntity.ok(result);
    }
}