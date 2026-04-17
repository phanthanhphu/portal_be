package org.bsl.portal.controller;

import org.bsl.portal.dto.FormResponse;
import org.bsl.portal.model.FormItem;
import org.bsl.portal.model.Department;
import org.bsl.portal.service.FormService;
import org.bsl.portal.service.DepartmentService;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    @Autowired
    private FormService service;

    @Autowired
    private DepartmentService departmentService;

    private final String FILE_DIR = "files/";

    // ==================== CREATE FORM ====================
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String departmentId,
            @RequestParam(required = false) MultipartFile file) {

        try {
            // Ràng buộc title
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required and cannot be empty"));
            }

            // Ràng buộc departmentId tồn tại
            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }
            Department dept = departmentService.getById(departmentId);
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            // Ràng buộc: title không được trùng trong cùng department
            if (service.existsByTitleAndDepartmentId(title.trim(), departmentId.trim())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title '" + title.trim() + "' already exists in this department"));
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
            item.setFileUrl(fileUrl);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());

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
            @RequestParam(required = false) MultipartFile file) {

        try {
            FormItem existing = service.getById(id);
            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Form not found"));
            }

            // Ràng buộc title
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required and cannot be empty"));
            }

            // Ràng buộc departmentId tồn tại
            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }
            Department dept = departmentService.getById(departmentId);
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            // Ràng buộc: title không được trùng trong cùng department (loại trừ chính record đang sửa)
            if (!title.trim().equals(existing.getTitle()) &&
                    service.existsByTitleAndDepartmentId(title.trim(), departmentId.trim())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title '" + title.trim() + "' already exists in this department"));
            }

            // Cập nhật các trường
            existing.setTitle(title.trim());
            existing.setDescription(description != null ? description.trim() : "");
            existing.setDepartmentId(departmentId.trim());

            // Không cho thay đổi createdAt (giữ nguyên giá trị cũ)
            existing.setUpdatedAt(LocalDateTime.now());

            // Xử lý file mới (nếu có)
            if (file != null && !file.isEmpty()) {
                // Xóa file cũ nếu tồn tại
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

                existing.setFileUrl("/files/" + fileName);
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
        return ResponseEntity.ok(service.getByDepartment(departmentId));
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        try {
            String[] sortParts = sort.split(",");
            Sort.Direction dir = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1].trim())
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            String field = sortParts[0].trim();

            Pageable pageable = PageRequest.of(page, size, Sort.by(dir, field));

            Page<FormResponse> result = service.search(
                    division,
                    departmentName,
                    title,
                    description,
                    pageable
            );

            Map<String, Object> response = new HashMap<>();
            response.put("content", result.getContent());
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
}