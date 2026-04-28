package org.bsl.portal.controller;

import org.bsl.portal.model.AppLink;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.User;
import org.bsl.portal.service.AppLinkService;
import org.bsl.portal.service.DepartmentService;
import org.bsl.portal.service.UserService;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/app-links")
public class AppLinkController {

    @Autowired
    private AppLinkService appLinkService;

    @Autowired
    private UserService userService;

    @Autowired
    private DepartmentService departmentService;

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
    // Admin: can choose departmentId
    // User: departmentId is forced to user's departmentId
    // ===============================
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String name,
            @RequestParam String url,
            @RequestParam String userId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String desc,
            @RequestPart(required = false) MultipartFile image
    ) {

        try {

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Name is required"));
            }

            if (url == null || !URL_PATTERN.matcher(url).matches()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "URL must start with http:// or https://"));
            }

            User user = getUserOrNull(userId);

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User with ID " + userId + " does not exist"));
            }

            boolean admin = isAdmin(user);

            String effectiveDepartmentId = resolveDepartmentIdForCreateOrUpdate(user, admin, departmentId);

            if (effectiveDepartmentId == null || effectiveDepartmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            Department department = departmentService.getById(effectiveDepartmentId);

            if (department == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + effectiveDepartmentId + " does not exist"));
            }

            if (appLinkService.existsByName(name.trim())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "AppLink name already exists"));
            }

            String imageUrl = saveImage(image);

            AppLink link = appLinkService.create(
                    name.trim(),
                    url.trim(),
                    imageUrl,
                    desc != null ? desc.trim() : "",
                    effectiveDepartmentId
            );

            return ResponseEntity.ok(toLinkResponse(link, admin, user.getDepartmentId()));

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create AppLink: " + e.getMessage()));
        }
    }

    // ===============================
    // UPDATE
    // Admin: can update any link and change departmentId
    // User: can update only links in their own department and cannot change departmentId
    // ===============================
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String name,
            @RequestParam String url,
            @RequestParam String userId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String desc,
            @RequestPart(required = false) MultipartFile image
    ) {

        try {

            AppLink existing = appLinkService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "AppLink not found"));
            }

            User user = getUserOrNull(userId);

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User with ID " + userId + " does not exist"));
            }

            boolean admin = isAdmin(user);

            if (!canModifyAppLink(user, admin, existing)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have permission to update this AppLink"));
            }

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Name is required"));
            }

            if (url == null || !URL_PATTERN.matcher(url).matches()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid URL format"));
            }

            String effectiveDepartmentId;

            if (admin) {
                effectiveDepartmentId = departmentId != null && !departmentId.trim().isEmpty()
                        ? departmentId.trim()
                        : existing.getDepartmentId();
            } else {
                effectiveDepartmentId = user.getDepartmentId();
            }

            if (effectiveDepartmentId == null || effectiveDepartmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            Department department = departmentService.getById(effectiveDepartmentId);

            if (department == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + effectiveDepartmentId + " does not exist"));
            }

            String imageUrl = existing.getIcon();

            if (image != null && !image.isEmpty()) {
                deleteImage(existing.getIcon());
                imageUrl = saveImage(image);
            }

            AppLink updated = appLinkService.update(
                    id,
                    name.trim(),
                    url.trim(),
                    imageUrl,
                    desc != null ? desc.trim() : "",
                    effectiveDepartmentId
            );

            return ResponseEntity.ok(toLinkResponse(updated, admin, user.getDepartmentId()));

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update AppLink: " + e.getMessage()));
        }
    }

    // ===============================
    // DELETE
    // Admin: can delete any link
    // User: can delete only links in their own department
    // ===============================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @RequestParam String userId
    ) {

        try {

            AppLink existing = appLinkService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "AppLink not found"));
            }

            User user = getUserOrNull(userId);

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User with ID " + userId + " does not exist"));
            }

            boolean admin = isAdmin(user);

            if (!canModifyAppLink(user, admin, existing)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have permission to delete this AppLink"));
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
    public ResponseEntity<?> getById(
            @PathVariable String id,
            @RequestParam(required = false) String userId
    ) {

        AppLink link = appLinkService.getById(id);

        if (link == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "AppLink not found"));
        }

        if (userId == null || userId.trim().isEmpty()) {
            return ResponseEntity.ok(link);
        }

        User user = getUserOrNull(userId);

        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "User with ID " + userId + " does not exist"));
        }

        boolean admin = isAdmin(user);

        return ResponseEntity.ok(toLinkResponse(link, admin, user.getDepartmentId()));
    }

    // ===============================
    // GET ALL PAGED
    // ===============================
    @GetMapping
    public ResponseEntity<?> getAllPaged(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        return searchPaged(null, null, userId, skipDepartmentFilter, page, size, "updatedAt", "desc");
    }

    // ===============================
    // SEARCH
    //
    // skipDepartmentFilter = true:
    // - Get links from all departments.
    // - UI can disable edit/delete if row.departmentId != currentDepartmentId.
    //
    // skipDepartmentFilter = false:
    // - Normal user only gets links from their own department.
    // - Admin still gets all links.
    // ===============================
    @GetMapping("/search")
    public ResponseEntity<?> searchPaged(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String desc,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        try {
            User user = null;
            boolean admin = false;
            String currentDepartmentId = null;

            if (userId != null && !userId.trim().isEmpty()) {
                user = getUserOrNull(userId.trim());

                if (user == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User with ID " + userId + " does not exist"));
                }

                admin = isAdmin(user);
                currentDepartmentId = user.getDepartmentId();
            }

            String effectiveSortBy = sortBy == null || sortBy.trim().isEmpty()
                    ? "updatedAt"
                    : sortBy.trim();

            Sort sort = sortDir.equalsIgnoreCase("asc")
                    ? Sort.by(effectiveSortBy).ascending()
                    : Sort.by(effectiveSortBy).descending();

            Pageable pageable = PageRequest.of(page, size, sort);

            Page<AppLink> result;

            if (user != null && !admin && !skipDepartmentFilter) {
                if (currentDepartmentId == null || currentDepartmentId.trim().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User does not belong to any department"));
                }

                result = appLinkService.getAllPagedWithSearch(
                        name,
                        desc,
                        currentDepartmentId,
                        pageable
                );
            } else {
                result = appLinkService.getAllPagedWithSearch(name, desc, pageable);
            }

            List<Map<String, Object>> content = new ArrayList<>();

            for (AppLink link : result.getContent()) {
                content.add(toLinkResponse(link, admin, currentDepartmentId));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("isAdmin", admin);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("skipDepartmentFilter", skipDepartmentFilter);
            response.put("disableDepartmentSelect", !admin);
            response.put("totalElements", result.getTotalElements());
            response.put("totalPages", result.getTotalPages());
            response.put("number", result.getNumber());
            response.put("size", result.getSize());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to search AppLinks: " + e.getMessage()));
        }
    }

    private User getUserOrNull(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        Optional<User> userOpt = userService.findById(userId.trim());

        return userOpt.orElse(null);
    }

    private String resolveDepartmentIdForCreateOrUpdate(User user, boolean admin, String requestDepartmentId) {
        if (admin) {
            return requestDepartmentId != null ? requestDepartmentId.trim() : null;
        }

        return user.getDepartmentId() != null ? user.getDepartmentId().trim() : null;
    }

    private boolean canModifyAppLink(User user, boolean admin, AppLink link) {
        if (admin) {
            return true;
        }

        if (user == null || link == null) {
            return false;
        }

        String userDepartmentId = user.getDepartmentId();
        String linkDepartmentId = link.getDepartmentId();

        if (userDepartmentId == null || linkDepartmentId == null) {
            return false;
        }

        return userDepartmentId.trim().equals(linkDepartmentId.trim());
    }

    private Map<String, Object> toLinkResponse(
            AppLink link,
            boolean admin,
            String currentDepartmentId
    ) {
        Map<String, Object> map = new HashMap<>();

        if (link == null) {
            return map;
        }

        String linkDepartmentId = link.getDepartmentId();

        Department department = null;

        if (linkDepartmentId != null && !linkDepartmentId.trim().isEmpty()) {
            department = departmentService.getById(linkDepartmentId.trim());
        }

        boolean sameDepartment = sameDepartment(currentDepartmentId, linkDepartmentId);
        boolean canModify = admin || sameDepartment;

        map.put("id", link.getId());
        map.put("name", link.getName());
        map.put("url", link.getUrl());
        map.put("desc", link.getDesc());
        map.put("icon", link.getIcon());

        map.put("departmentId", linkDepartmentId);
        map.put("departmentName", department != null ? department.getDepartmentName() : null);
        map.put("division", department != null ? department.getDivision() : null);

        map.put("createdAt", link.getCreatedAt());
        map.put("updatedAt", link.getUpdatedAt());

        map.put("canEdit", canModify);
        map.put("canDelete", canModify);

        return map;
    }

    private boolean sameDepartment(String currentDepartmentId, String linkDepartmentId) {
        if (currentDepartmentId == null || linkDepartmentId == null) {
            return false;
        }

        return currentDepartmentId.trim().equals(linkDepartmentId.trim());
    }

    private boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();

        return "Admin".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role)
                || "ROLE_ADMIN".equalsIgnoreCase(role);
    }
}
