package org.bsl.portal.controller;

import org.bsl.portal.model.DocumentType;
import org.bsl.portal.service.DocumentTypeService;
import org.bsl.portal.service.FormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

    @Autowired
    private DocumentTypeService service;

    @Autowired
    private FormService formService;

    // ==================== CREATE TYPE ====================
    @PostMapping
    public ResponseEntity<?> create(@RequestBody DocumentType type) {
        try {
            DocumentType created = service.create(type);
            return ResponseEntity.ok(created);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Create document type failed: " + e.getMessage()));
        }
    }

    // ==================== UPDATE TYPE ====================
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody DocumentType type
    ) {
        try {
            DocumentType updated = service.update(id, type);
            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update document type failed: " + e.getMessage()));
        }
    }

    // ==================== DELETE TYPE ====================
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
                    .body(Map.of("message", "Delete document type failed: " + e.getMessage()));
        }
    }

    // ==================== GET ALL TYPES ====================
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch document types failed: " + e.getMessage()));
        }
    }

    // ==================== GET TYPE BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        try {
            DocumentType type = service.getById(id);

            if (type == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Document type not found"));
            }

            return ResponseEntity.ok(type);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch document type failed: " + e.getMessage()));
        }
    }

    // ==================== SEARCH BY NAME ====================
// ==================== SEARCH BY NAME WITH PAGINATION ====================
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            Page<DocumentType> result = service.searchByName(name, page, size);

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
                    .body(Map.of("message", "Search document types failed: " + e.getMessage()));
        }
    }

    // ==================== SYNC DEPARTMENT LIST BY TYPE ====================
    // Dùng 1 lần nếu database đã có forms cũ trước khi thêm field departments.
    @PostMapping("/{id}/sync-departments")
    public ResponseEntity<?> syncDepartmentsByType(@PathVariable String id) {
        try {
            formService.syncDepartmentsForType(id);
            return ResponseEntity.ok(Map.of("message", "Synced departments for document type successfully"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Sync departments failed: " + e.getMessage()));
        }
    }

    // ==================== SYNC ALL TYPE DEPARTMENTS ====================
    // Dùng 1 lần để rebuild lại departments cho toàn bộ document types từ forms hiện có.
    @PostMapping("/sync-departments")
    public ResponseEntity<?> syncAllDepartments() {
        try {
            formService.syncDepartmentsForAllTypes();
            return ResponseEntity.ok(Map.of("message", "Synced departments for all document types successfully"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Sync all departments failed: " + e.getMessage()));
        }
    }
}
