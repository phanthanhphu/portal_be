package org.bsl.portal.controller;

import org.bsl.portal.dto.FormResponse;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.DocumentType;
import org.bsl.portal.model.FormItem;
import org.bsl.portal.model.User;
import org.bsl.portal.service.DepartmentService;
import org.bsl.portal.service.DocumentTypeService;
import org.bsl.portal.service.FormService;
import org.bsl.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    @Autowired
    private FormService service;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DocumentTypeService documentTypeService;

    @Autowired
    private UserService userService;

    private final String FILE_DIR = "files/";

    // ==================== CREATE FORM ====================
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String departmentId,
            @RequestParam String typeId,
            @RequestParam(required = false) MultipartFile file
    ) {
        try {
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required and cannot be empty"));
            }

            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            if (typeId == null || typeId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Type ID is required"));
            }

            Department dept = departmentService.getById(departmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            DocumentType type = documentTypeService.getById(typeId.trim());
            if (type == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Document type with ID " + typeId + " does not exist"));
            }

            if (service.existsByTitleAndDepartmentIdAndTypeId(
                    title.trim(),
                    departmentId.trim(),
                    typeId.trim()
            )) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title '" + title.trim() + "' already exists in this department and type"));
            }

            String fileUrl = null;

            if (file != null && !file.isEmpty()) {
                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Path path = Paths.get(FILE_DIR + fileName);
                Files.createDirectories(path.getParent());

                try {
                    Files.write(path, file.getBytes());
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("message", "Failed to save file: " + e.getMessage()));
                }

                fileUrl = "/files/" + fileName;
            }

            FormItem item = new FormItem();
            item.setTitle(title.trim());
            item.setDescription(description != null ? description.trim() : "");
            item.setDepartmentId(departmentId.trim());
            item.setTypeId(typeId.trim());
            item.setFileUrl(fileUrl);
            item.setPreviewUrl(fileUrl);

            LocalDateTime now = LocalDateTime.now();
            item.setCreatedAt(now);
            item.setUpdatedAt(now);

            FormItem created = service.create(item);

            return ResponseEntity.ok(created);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Create failed: " + e.getMessage()));
        }
    }

    // ==================== UPDATE FORM ====================
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String departmentId,
            @RequestParam String typeId,
            @RequestParam(required = false) MultipartFile file
    ) {
        try {
            FormItem existing = service.getById(id);
            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Form not found"));
            }

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required and cannot be empty"));
            }

            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            if (typeId == null || typeId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Type ID is required"));
            }

            Department dept = departmentService.getById(departmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            DocumentType type = documentTypeService.getById(typeId.trim());
            if (type == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Document type with ID " + typeId + " does not exist"));
            }

            String newTitle = title.trim();
            String newDepartmentId = departmentId.trim();
            String newTypeId = typeId.trim();

            boolean titleChanged = !newTitle.equals(existing.getTitle());

            boolean departmentChanged = existing.getDepartmentId() == null
                    || !newDepartmentId.equals(existing.getDepartmentId());

            boolean typeChanged = existing.getTypeId() == null
                    || !newTypeId.equals(existing.getTypeId());

            if ((titleChanged || departmentChanged || typeChanged)
                    && service.existsByTitleAndDepartmentIdAndTypeId(newTitle, newDepartmentId, newTypeId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title '" + newTitle + "' already exists in this department and type"));
            }

            existing.setTitle(newTitle);
            existing.setDescription(description != null ? description.trim() : "");
            existing.setDepartmentId(newDepartmentId);
            existing.setTypeId(newTypeId);
            existing.setUpdatedAt(LocalDateTime.now());

            if (file != null && !file.isEmpty()) {
                if (existing.getFileUrl() != null) {
                    Path oldPath = Paths.get(existing.getFileUrl().replace("/files/", FILE_DIR));
                    Files.deleteIfExists(oldPath);
                }

                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
                Path path = Paths.get(FILE_DIR + fileName);

                Files.createDirectories(path.getParent());

                try {
                    Files.write(path, file.getBytes());
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("message", "Failed to save new file: " + e.getMessage()));
                }

                String fileUrl = "/files/" + fileName;
                existing.setFileUrl(fileUrl);
                existing.setPreviewUrl(fileUrl);
            }

            FormItem updated = service.update(id, existing);

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update failed: " + e.getMessage()));
        }
    }

    // ==================== DELETE FORM ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            FormItem existing = service.getById(id);

            if (existing != null && existing.getFileUrl() != null) {
                Path path = Paths.get(existing.getFileUrl().replace("/files/", FILE_DIR));
                Files.deleteIfExists(path);
            }

            service.delete(id);

            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Delete failed: " + e.getMessage()));
        }
    }

    // ==================== GET ALL ====================
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch forms: " + e.getMessage()));
        }
    }

    // ==================== GET BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        FormItem form = service.getById(id);

        if (form == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Form not found"));
        }

        return ResponseEntity.ok(form);
    }

    // ==================== GET BY DEPARTMENT ====================
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<?> getByDepartment(@PathVariable String departmentId) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "DepartmentId is required"));
        }

        return ResponseEntity.ok(service.getByDepartment(departmentId.trim()));
    }

    // ==================== GET BY TYPE ====================
    @GetMapping("/type/{typeId}")
    public ResponseEntity<?> getByType(@PathVariable String typeId) {
        if (typeId == null || typeId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "TypeId is required"));
        }

        return ResponseEntity.ok(service.getByTypeId(typeId.trim()));
    }

    // ==================== SEARCH ====================
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String typeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User currentUser = null;
            boolean admin = false;
            String currentDepartmentId = null;
            String filterDepartmentId = null;

            if (userId != null && !userId.trim().isEmpty()) {
                Optional<User> userOpt = userService.findById(userId.trim());

                if (userOpt.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User with ID " + userId + " does not exist"));
                }

                currentUser = userOpt.get();
                admin = isAdmin(currentUser);
                currentDepartmentId = currentUser.getDepartmentId();

                if (!admin && !skipDepartmentFilter) {
                    if (currentDepartmentId == null || currentDepartmentId.trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("message", "User does not belong to any department"));
                    }

                    filterDepartmentId = currentDepartmentId.trim();
                }
            }

            Pageable pageable = PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Direction.DESC, "updatedAt")
                            .and(Sort.by(Sort.Direction.DESC, "createdAt"))
            );

            Page<FormResponse> result = service.search(
                    filterDepartmentId,
                    division,
                    departmentName,
                    title,
                    description,
                    typeId,
                    pageable
            );

            List<Map<String, Object>> content = new ArrayList<>();

            for (FormResponse form : result.getContent()) {
                content.add(toFormResponseMap(form, admin, currentDepartmentId));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("isAdmin", admin);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("skipDepartmentFilter", skipDepartmentFilter);
            response.put("disableDepartmentSearch", !admin && !skipDepartmentFilter);
            response.put("totalElements", result.getTotalElements());
            response.put("totalPages", result.getTotalPages());
            response.put("number", result.getNumber());
            response.put("size", result.getSize());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to search forms: " + e.getMessage()));
        }
    }

    private Map<String, Object> toFormResponseMap(
            FormResponse form,
            boolean admin,
            String currentDepartmentId
    ) {
        Map<String, Object> map = new HashMap<>();

        String formDepartmentId = form.getDepartmentId();
        boolean canModify = admin || sameDepartment(currentDepartmentId, formDepartmentId);

        map.put("id", form.getId());
        map.put("departmentId", formDepartmentId);
        map.put("typeId", form.getTypeId());
        map.put("departmentName", form.getDepartmentName());
        map.put("division", form.getDivision());
        map.put("title", form.getTitle());
        map.put("description", form.getDescription());
        map.put("fileType", form.getFileType());
        map.put("fileUrl", form.getFileUrl());
        map.put("previewUrl", form.getPreviewUrl());
        map.put("createdAt", form.getCreatedAt());
        map.put("updatedAt", form.getUpdatedAt());

        map.put("canEdit", canModify);
        map.put("canDelete", canModify);

        return map;
    }

    private boolean sameDepartment(String currentDepartmentId, String itemDepartmentId) {
        if (currentDepartmentId == null || itemDepartmentId == null) {
            return false;
        }

        return currentDepartmentId.trim().equals(itemDepartmentId.trim());
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