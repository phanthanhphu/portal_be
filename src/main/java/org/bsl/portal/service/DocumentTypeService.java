package org.bsl.portal.service;

import org.bsl.portal.model.Department;
import org.bsl.portal.model.DocumentType;
import org.bsl.portal.model.DocumentTypeDepartment;
import org.bsl.portal.repository.DocumentTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class DocumentTypeService {

    @Autowired
    private DocumentTypeRepository repository;

    // ==================== CREATE ====================
    public DocumentType create(DocumentType type) {
        validateName(type);

        String name = type.getName().trim();

        if (repository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Document type name '" + name + "' already exists");
        }

        if (type.getId() == null || type.getId().trim().isEmpty()) {
            type.setId(UUID.randomUUID().toString());
        }

        LocalDateTime now = LocalDateTime.now();

        type.setName(name);
        type.setDepartments(normalizeDepartments(type.getDepartments()));
        type.setCreatedAt(now);
        type.setUpdatedAt(now);

        return repository.save(type);
    }

    // ==================== UPDATE ====================
    public DocumentType update(String id, DocumentType newType) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Document type ID is required");
        }

        validateName(newType);

        DocumentType existing = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Document type not found"));

        String newName = newType.getName().trim();

        Optional<DocumentType> duplicate = repository.findByNameIgnoreCase(newName);

        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            throw new IllegalArgumentException("Document type name '" + newName + "' already exists");
        }

        existing.setName(newName);

        // Không cho update API ghi đè list departments một cách tùy tiện.
        // List này được đồng bộ tự động theo Form create/update/delete.
        if (existing.getDepartments() == null) {
            existing.setDepartments(new ArrayList<>());
        }

        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    // ==================== DELETE ====================
    public void delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Document type ID is required");
        }

        DocumentType existing = repository.findById(id.trim())
                .orElseThrow(() -> new NoSuchElementException("Document type not found"));

        if (existing.getDepartments() != null && !existing.getDepartments().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete document type because it still has departments"
            );
        }

        repository.deleteById(id.trim());
    }

    // ==================== GET ALL ====================
    public List<DocumentType> getAll() {
        List<DocumentType> types = repository.findAll();
        types.forEach(this::ensureDepartmentList);
        return types;
    }

    // ==================== GET BY ID ====================
    public DocumentType getById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }

        DocumentType type = repository.findById(id).orElse(null);
        ensureDepartmentList(type);
        return type;
    }

    // ==================== SEARCH BY NAME ====================
// ==================== SEARCH BY NAME WITH PAGINATION ====================
    public Page<DocumentType> searchByName(String name, int page, int size) {
        int safePage = Math.max(page, 0);

        int safeSize = size <= 0 ? 10 : size;
        safeSize = Math.min(safeSize, 50);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.ASC, "name")
        );

        Page<DocumentType> types;

        if (name == null || name.trim().isEmpty()) {
            types = repository.findAll(pageable);
        } else {
            types = repository.findByNameContainingIgnoreCase(name.trim(), pageable);
        }

        types.getContent().forEach(this::ensureDepartmentList);

        return types;
    }

    // ==================== ADD DEPARTMENT TO TYPE ====================
    public void addDepartmentToType(String typeId, Department department) {
        if (typeId == null || typeId.trim().isEmpty() || department == null) {
            return;
        }

        String departmentId = normalize(department.getId());
        if (departmentId == null) {
            return;
        }

        DocumentType type = repository.findById(typeId.trim()).orElse(null);
        if (type == null) {
            return;
        }

        List<DocumentTypeDepartment> departments = normalizeDepartments(type.getDepartments());
        String departmentName = normalize(department.getDepartmentName());

        boolean exists = departments.stream()
                .anyMatch(item -> departmentId.equals(normalize(item.getIdDepartment())));

        if (!exists) {
            departments.add(new DocumentTypeDepartment(departmentId, departmentName));
        } else {
            departments.forEach(item -> {
                if (departmentId.equals(normalize(item.getIdDepartment()))) {
                    item.setName(departmentName);
                }
            });
        }

        type.setDepartments(sortDepartments(departments));
        type.setUpdatedAt(LocalDateTime.now());
        repository.save(type);
    }

    // ==================== REMOVE DEPARTMENT FROM TYPE ====================
    public void removeDepartmentFromType(String typeId, String departmentId) {
        String normalizedTypeId = normalize(typeId);
        String normalizedDepartmentId = normalize(departmentId);

        if (normalizedTypeId == null || normalizedDepartmentId == null) {
            return;
        }

        DocumentType type = repository.findById(normalizedTypeId).orElse(null);
        if (type == null) {
            return;
        }

        List<DocumentTypeDepartment> departments = normalizeDepartments(type.getDepartments());
        int oldSize = departments.size();

        departments.removeIf(item -> normalizedDepartmentId.equals(normalize(item.getIdDepartment())));

        if (departments.size() != oldSize) {
            type.setDepartments(departments);
            type.setUpdatedAt(LocalDateTime.now());
            repository.save(type);
        }
    }

    // ==================== REPLACE DEPARTMENTS IN TYPE ====================
    public DocumentType replaceDepartments(String typeId, List<Department> sourceDepartments) {
        String normalizedTypeId = normalize(typeId);

        if (normalizedTypeId == null) {
            throw new IllegalArgumentException("Document type ID is required");
        }

        DocumentType type = repository.findById(normalizedTypeId)
                .orElseThrow(() -> new NoSuchElementException("Document type not found"));

        Map<String, DocumentTypeDepartment> unique = new LinkedHashMap<>();

        if (sourceDepartments != null) {
            for (Department dept : sourceDepartments) {
                if (dept == null) continue;

                String deptId = normalize(dept.getId());
                if (deptId == null) continue;

                unique.put(deptId, new DocumentTypeDepartment(deptId, normalize(dept.getDepartmentName())));
            }
        }

        type.setDepartments(sortDepartments(new ArrayList<>(unique.values())));
        type.setUpdatedAt(LocalDateTime.now());

        return repository.save(type);
    }

    private void validateName(DocumentType type) {
        if (type == null) {
            throw new IllegalArgumentException("Document type body is required");
        }

        if (type.getName() == null || type.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Document type name is required");
        }
    }

    private void ensureDepartmentList(DocumentType type) {
        if (type != null && type.getDepartments() == null) {
            type.setDepartments(new ArrayList<>());
        }
    }

    private List<DocumentTypeDepartment> normalizeDepartments(List<DocumentTypeDepartment> departments) {
        Map<String, DocumentTypeDepartment> unique = new LinkedHashMap<>();

        if (departments != null) {
            for (DocumentTypeDepartment dept : departments) {
                if (dept == null) continue;

                String deptId = normalize(dept.getIdDepartment());
                if (deptId == null) continue;

                unique.put(deptId, new DocumentTypeDepartment(deptId, normalize(dept.getName())));
            }
        }

        return sortDepartments(new ArrayList<>(unique.values()));
    }

    private List<DocumentTypeDepartment> sortDepartments(List<DocumentTypeDepartment> departments) {
        departments.sort(
                Comparator.comparing(
                        item -> normalize(item.getName()) != null ? normalize(item.getName()).toLowerCase() : "",
                        Comparator.nullsLast(String::compareTo)
                )
        );

        return departments;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }
}
