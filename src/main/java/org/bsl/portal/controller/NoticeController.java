package org.bsl.portal.controller;

import org.bsl.portal.dto.NoticeResponse;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.Notice;
import org.bsl.portal.service.DepartmentService;
import org.bsl.portal.service.NoticeService;
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
@RequestMapping("/api/notices")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private DepartmentService departmentService;

    private final String FILE_DIR = "files/";

    // CREATE NOTICE
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam String userId,
            @RequestParam String departmentId,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) MultipartFile file) {

        try {

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required"));
            }

            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            Department dept = departmentService.getById(departmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            String fileUrl = null;

            if (file != null && !file.isEmpty()) {

                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

                Path path = Paths.get(FILE_DIR + fileName);

                Files.createDirectories(path.getParent());

                Files.write(path, file.getBytes());

                fileUrl = "/files/" + fileName;
            }

            Notice notice = new Notice();

            notice.setTitle(title.trim());
            notice.setContent(content != null ? content.trim() : "");
            notice.setUserId(userId);
            notice.setDepartmentId(departmentId.trim());
            notice.setPinned(pinned != null ? pinned : false);
            notice.setFileUrl(fileUrl);
            notice.setCreatedAt(LocalDateTime.now());
            notice.setUpdatedAt(LocalDateTime.now());

            Notice created = noticeService.create(notice);

            return ResponseEntity.ok(created);

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Create failed: " + e.getMessage()));
        }
    }

    // UPDATE NOTICE
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam String departmentId,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) MultipartFile file) {

        try {

            Notice existing = noticeService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required"));
            }

            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            Department dept = departmentService.getById(departmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            existing.setTitle(title.trim());
            existing.setContent(content != null ? content.trim() : "");
            existing.setDepartmentId(departmentId.trim());
            existing.setPinned(pinned != null ? pinned : false);

            if (file != null && !file.isEmpty()) {

                if (existing.getFileUrl() != null) {
                    Path oldPath = Paths.get(existing.getFileUrl().replace("/files/", "files/"));
                    Files.deleteIfExists(oldPath);
                }

                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

                Path path = Paths.get(FILE_DIR + fileName);

                Files.createDirectories(path.getParent());

                Files.write(path, file.getBytes());

                existing.setFileUrl("/files/" + fileName);
            }

            existing.setUpdatedAt(LocalDateTime.now());

            Notice updated = noticeService.update(id, existing);

            return ResponseEntity.ok(updated);

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update failed: " + e.getMessage()));
        }
    }

    // DELETE NOTICE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {

        Notice existing = noticeService.getById(id);

        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notice not found"));
        }

        try {

            if (existing.getFileUrl() != null) {

                Path path = Paths.get(existing.getFileUrl().replace("/files/", "files/"));

                Files.deleteIfExists(path);
            }

            noticeService.delete(id);

            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Delete failed: " + e.getMessage()));
        }
    }

    // GET NOTICE BY ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {

        Notice notice = noticeService.getById(id);

        if (notice == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notice not found"));
        }

        return ResponseEntity.ok(notice);
    }

    // GET ALL PAGED
    @GetMapping
    public ResponseEntity<?> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Notice> result = noticeService.getAllPaged(page, size);

        return ResponseEntity.ok(result);
    }

    // PIN NOTICE
    @PatchMapping("/{id}/pin")
    public ResponseEntity<?> pin(
            @PathVariable String id,
            @RequestParam Boolean pinned) {

        Notice notice = noticeService.pin(id, pinned);

        if (notice == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notice not found"));
        }

        return ResponseEntity.ok(notice);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content,
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

            Page<NoticeResponse> result = noticeService.search(
                    division,
                    departmentName,
                    title,
                    content,
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
                    .body(Map.of("message", "Search failed: " + e.getMessage()));
        }
    }
}
