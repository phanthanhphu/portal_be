package org.bsl.portal.controller;

import org.bsl.portal.model.Department;
import org.bsl.portal.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService service;

    /*
    ===============================
    CREATE
    ===============================
    */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam String division,
            @RequestParam String departmentName
    ) {
        try {

            // validate
            if (division == null || division.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("status", 400, "message", "Division is required"));
            }

            if (departmentName == null || departmentName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("status", 400, "message", "Department name is required"));
            }

            Department department = service.create(division, departmentName);

            return ResponseEntity.ok(department);

        } catch (RuntimeException ex) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", 400,
                            "message", ex.getMessage()
                    ));
        }
    }

    /*
    ===============================
    GET ALL
    ===============================
    */
    @GetMapping
    public ResponseEntity<?> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    /*
    ===============================
    GET BY ID
    ===============================
    */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {

        Department department = service.getById(id);

        if (department == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", 404,
                            "message", "Department not found"
                    ));
        }

        return ResponseEntity.ok(department);
    }

    /*
    ===============================
    UPDATE
    ===============================
    */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String division,
            @RequestParam String departmentName
    ) {
        try {

            // validate
            if (division == null || division.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("status", 400, "message", "Division is required"));
            }

            if (departmentName == null || departmentName.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("status", 400, "message", "Department name is required"));
            }

            Department department = service.update(id, division, departmentName);

            if (department == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "status", 404,
                                "message", "Department not found"
                        ));
            }

            return ResponseEntity.ok(department);

        } catch (RuntimeException ex) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "status", 400,
                            "message", ex.getMessage()
                    ));
        }
    }

    /*
    ===============================
    DELETE
    ===============================
    */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {

            service.delete(id);

            return ResponseEntity.ok(Map.of(
                    "status", 200,
                    "message", "Deleted successfully"
            ));

        } catch (RuntimeException ex) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "status", 404,
                            "message", ex.getMessage()
                    ));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> filter(
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String departmentName
    ) {
        List<Department> departments = service.getAll(division, departmentName);
        return ResponseEntity.ok(departments);
    }
}