package org.bsl.portal.service;

import org.bsl.portal.model.Department;
import org.bsl.portal.repository.DepartmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository repository;

    /*
    ===============================
    CREATE
    ===============================
    */
    public Department create(String division, String departmentName) {

        // normalize input
        division = division != null ? division.trim() : "";
        departmentName = departmentName != null ? departmentName.trim() : "";

        // validate
        if (division.isEmpty()) {
            throw new RuntimeException("Division is required");
        }

        if (departmentName.isEmpty()) {
            throw new RuntimeException("Department name is required");
        }

        // check duplicate (division + departmentName)
        boolean exists = repository
                .existsByDivisionAndDepartmentName(division, departmentName);

        if (exists) {
            throw new RuntimeException(
                    "Department already exists in this division"
            );
        }

        // create
        Department department = new Department();
        department.setDivision(division);
        department.setDepartmentName(departmentName);

        LocalDateTime now = LocalDateTime.now();
        department.setCreatedAt(now);
        department.setUpdatedAt(now);

        return repository.save(department);
    }

    /*
    ===============================
    GET ALL
    ===============================
    */
    public List<Department> getAll() {
        return repository.findAll();
    }

    public List<Department> getAll(String division, String departmentName) {
        // Normalize the filters
        division = division != null ? division.trim() : "";
        departmentName = departmentName != null ? departmentName.trim() : "";

        if (division.isEmpty() && departmentName.isEmpty()) {
            // If both are empty, return all departments
            return repository.findAll();
        } else if (!division.isEmpty() && departmentName.isEmpty()) {
            // If only division is provided, filter by division
            return repository.findByDivisionContainingIgnoreCase(division);
        } else if (division.isEmpty() && !departmentName.isEmpty()) {
            // If only departmentName is provided, filter by departmentName
            return repository.findByDepartmentNameContainingIgnoreCase(departmentName);
        } else {
            // If both are provided, filter by both division and departmentName
            return repository.findByDivisionContainingIgnoreCaseAndDepartmentNameContainingIgnoreCase(division, departmentName);
        }
    }

    /*
    ===============================
    SEARCH
    ===============================
    */
    public List<Department> search(String division, String departmentName, int page, int size) {
        List<Department> result = getAll(division, departmentName);

        if (page < 0) page = 0;
        if (size <= 0) size = result.size() > 0 ? result.size() : 1;

        int fromIndex = page * size;
        if (fromIndex >= result.size()) {
            return List.of();
        }

        int toIndex = Math.min(fromIndex + size, result.size());
        return result.subList(fromIndex, toIndex);
    }

    /*
    ===============================
    GET BY ID
    ===============================
    */
    public Department getById(String id) {
        return repository.findById(id).orElse(null);
    }

    /*
    ===============================
    UPDATE
    ===============================
    */
    public Department update(String id, String division, String departmentName) {

        // normalize
        division = division != null ? division.trim() : "";
        departmentName = departmentName != null ? departmentName.trim() : "";

        // validate
        if (division.isEmpty()) {
            throw new RuntimeException("Division is required");
        }

        if (departmentName.isEmpty()) {
            throw new RuntimeException("Department name is required");
        }

        Optional<Department> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        // check duplicate (but ignore itself)
        Optional<Department> duplicate = repository
                .findByDivisionAndDepartmentName(division, departmentName);

        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            throw new RuntimeException(
                    "Department already exists in this division"
            );
        }

        Department existing = optional.get();

        existing.setDivision(division);
        existing.setDepartmentName(departmentName);
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    /*
    ===============================
    DELETE
    ===============================
    */
    public void delete(String id) {

        if (!repository.existsById(id)) {
            throw new RuntimeException("Department not found");
        }

        repository.deleteById(id);
    }

    public Department findByDepartmentName(String departmentName) {
        return repository.findByDepartmentNameIgnoreCase(departmentName)
                .orElse(null);
    }
}
