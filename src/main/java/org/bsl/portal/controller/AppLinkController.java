package org.bsl.portal.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.bsl.portal.common.socket.AppSocketPublisher;
import org.bsl.portal.model.AppLink;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.User;
import org.bsl.portal.security.JwtUtil;
import org.bsl.portal.service.AppLinkService;
import org.bsl.portal.service.DepartmentService;
import org.bsl.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/app-links")
public class AppLinkController {

    private static final String MODULE_APP_LINK = "APP_LINK";
    private static final String UPLOAD_DIR = "uploads/";
    private static final Pattern URL_PATTERN =
            Pattern.compile("^(http|https)://.*$", Pattern.CASE_INSENSITIVE);

    @Autowired
    private AppLinkService appLinkService;

    @Autowired
    private UserService userService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private AppSocketPublisher appSocketPublisher;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String name,
            @RequestParam String url,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String desc,
            @RequestPart(required = false) MultipartFile image,
            HttpServletRequest request
    ) {
        try {
            User user = getAuthenticatedUser(request);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication is required"));
            }

            if (!canManageAppLinks(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "APP_LINK permission is required"));
            }

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Name is required"));
            }

            if (url == null || !URL_PATTERN.matcher(url).matches()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "URL must start with http:// or https://"));
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

            appSocketPublisher.appLinkChanged("CREATED", link.getId());

            return ResponseEntity.ok(toLinkResponse(link, user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to create AppLink: " + e.getMessage()));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String name,
            @RequestParam String url,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) String desc,
            @RequestPart(required = false) MultipartFile image,
            HttpServletRequest request
    ) {
        try {
            User user = getAuthenticatedUser(request);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication is required"));
            }

            if (!canManageAppLinks(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "APP_LINK permission is required"));
            }

            AppLink existing = appLinkService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "AppLink not found"));
            }

            if (!canModifyAppLink(user, existing)) {
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

            boolean admin = isAdmin(user);
            String effectiveDepartmentId = admin
                    ? (departmentId != null && !departmentId.trim().isEmpty()
                        ? departmentId.trim()
                        : existing.getDepartmentId())
                    : user.getDepartmentId();

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

            appSocketPublisher.appLinkChanged("UPDATED", id);

            return ResponseEntity.ok(toLinkResponse(updated, user));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to update AppLink: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @RequestParam(required = false) String userId,
            HttpServletRequest request
    ) {
        try {
            User user = getAuthenticatedUser(request);

            if (user == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Authentication is required"));
            }

            if (!canManageAppLinks(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "APP_LINK permission is required"));
            }

            AppLink existing = appLinkService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "AppLink not found"));
            }

            if (!canModifyAppLink(user, existing)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have permission to delete this AppLink"));
            }

            deleteImage(existing.getIcon());
            appLinkService.delete(id);
            appSocketPublisher.appLinkChanged("DELETED", id);

            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete AppLink: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @PathVariable String id,
            @RequestParam(required = false) String userId,
            HttpServletRequest request
    ) {
        AppLink link = appLinkService.getById(id);

        if (link == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "AppLink not found"));
        }

        User user = getAuthenticatedUser(request);
        return ResponseEntity.ok(toLinkResponse(link, user));
    }

    @GetMapping
    public ResponseEntity<?> getAllPaged(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request
    ) {
        return searchPaged(
                null,
                null,
                userId,
                skipDepartmentFilter,
                page,
                size,
                "updatedAt",
                "desc",
                request
        );
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchPaged(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String desc,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpServletRequest request
    ) {
        try {
            User user = getAuthenticatedUser(request);
            boolean admin = isAdmin(user);
            boolean view = isViewRole(user);
            boolean canManage = canManageAppLinks(user);
            String currentDepartmentId = user != null ? clean(user.getDepartmentId()) : null;

            String effectiveSortBy = sortBy == null || sortBy.trim().isEmpty()
                    ? "updatedAt"
                    : sortBy.trim();

            Sort sort = sortDir.equalsIgnoreCase("asc")
                    ? Sort.by(effectiveSortBy).ascending()
                    : Sort.by(effectiveSortBy).descending();

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<AppLink> result;

            if (user != null
                    && !admin
                    && !view
                    && !skipDepartmentFilter
                    && currentDepartmentId != null) {
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
                content.add(toLinkResponse(link, user));
            }

            Department currentDepartment = currentDepartmentId != null
                    ? departmentService.getById(currentDepartmentId)
                    : null;

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("isAdmin", admin);
            response.put("isView", view);
            response.put("canManageAppLinks", canManage);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("currentDepartmentName",
                    currentDepartment != null ? currentDepartment.getDepartmentName() : null);
            response.put("currentDepartmentCode",
                    currentDepartment != null ? currentDepartment.getDivision() : null);
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

    private String saveImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String contentType = file.getContentType();

        if (!java.util.Arrays.asList("image/jpeg", "image/png", "image/gif").contains(contentType)) {
            throw new IOException("Only JPEG, PNG, GIF allowed");
        }

        String originalName = file.getOriginalFilename() != null
                ? Paths.get(file.getOriginalFilename()).getFileName().toString()
                : "image";
        String fileName = System.currentTimeMillis() + "_" + originalName;
        Path path = Paths.get(UPLOAD_DIR, fileName).normalize();

        Files.createDirectories(path.getParent());
        file.transferTo(path);

        return "/uploads/" + fileName;
    }

    private void deleteImage(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                return;
            }

            String fileName = Paths.get(imageUrl.replace("/uploads/", ""))
                    .getFileName()
                    .toString();
            Files.deleteIfExists(Paths.get(UPLOAD_DIR, fileName).normalize());
        } catch (Exception ignored) {
        }
    }

    private User getAuthenticatedUser(HttpServletRequest request) {
        String token = extractBearerToken(request != null ? request.getHeader("Authorization") : null);

        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }

        String email = jwtUtil.getEmailFromToken(token);

        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        return userService.findByEmail(email.trim()).orElse(null);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private String resolveDepartmentIdForCreateOrUpdate(
            User user,
            boolean admin,
            String requestDepartmentId
    ) {
        if (admin) {
            return clean(requestDepartmentId);
        }

        return clean(user != null ? user.getDepartmentId() : null);
    }

    private boolean canModifyAppLink(User user, AppLink link) {
        if (!canManageAppLinks(user) || link == null) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        return sameDepartment(user.getDepartmentId(), link.getDepartmentId());
    }

    private boolean canManageAppLinks(User user) {
        if (user == null || isViewRole(user)) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        String permission = String.valueOf(user.getModulePermission()).toUpperCase();

        for (String item : permission.split(",")) {
            if (MODULE_APP_LINK.equals(item.trim())) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Object> toLinkResponse(AppLink link, User user) {
        Map<String, Object> map = new HashMap<>();

        if (link == null) {
            return map;
        }

        String linkDepartmentId = clean(link.getDepartmentId());
        Department department = linkDepartmentId != null
                ? departmentService.getById(linkDepartmentId)
                : null;
        boolean canModify = canModifyAppLink(user, link);

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

    private boolean sameDepartment(String first, String second) {
        String left = clean(first);
        String right = clean(second);
        return left != null && right != null && left.equals(right);
    }

    private String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();
        return "ADMIN".equalsIgnoreCase(role)
                || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    private boolean isViewRole(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();
        return "VIEW".equalsIgnoreCase(role)
                || "ROLE_VIEW".equalsIgnoreCase(role);
    }
}
