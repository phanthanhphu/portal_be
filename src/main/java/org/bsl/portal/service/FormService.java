package org.bsl.portal.service;

import org.bsl.portal.dto.FormResponse;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.FormItem;
import org.bsl.portal.repository.FormRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FormService {

    @Autowired
    private FormRepository repository;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DocumentTypeService documentTypeService;

    @Autowired
    private MongoTemplate mongoTemplate;

    // ==================== CHECK TITLE TRÙNG TRONG CÙNG DEPARTMENT ====================
    public boolean existsByTitleAndDepartmentId(String title, String departmentId) {
        return repository.existsByTitleAndDepartmentId(title, departmentId);
    }

    // ==================== CHECK TITLE TRÙNG TRONG CÙNG DEPARTMENT + TYPE ====================
    public boolean existsByTitleAndDepartmentIdAndTypeId(String title, String departmentId, String typeId) {
        return repository.existsByTitleAndDepartmentIdAndTypeId(title, departmentId, typeId);
    }

    // ==================== CREATE ====================
    public FormItem create(FormItem item) {
        if (item.getId() == null || item.getId().isEmpty()) {
            item.setId(UUID.randomUUID().toString());
        }

        LocalDateTime now = LocalDateTime.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        FormItem saved = repository.save(item);
        addDepartmentMappingToType(saved.getTypeId(), saved.getDepartmentId());

        return saved;
    }

    // ==================== UPDATE ====================
    public FormItem update(String id, FormItem newItem) {
        Optional<FormItem> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        FormItem existing = optional.get();

        String oldDepartmentId = existing.getDepartmentId();
        String oldTypeId = existing.getTypeId();

        existing.setTitle(newItem.getTitle());
        existing.setDescription(newItem.getDescription());
        existing.setDepartmentId(newItem.getDepartmentId());
        existing.setTypeId(newItem.getTypeId());

        if (newItem.getFileUrl() != null && !newItem.getFileUrl().isEmpty()) {
            existing.setFileUrl(newItem.getFileUrl());
            existing.setFileType(newItem.getFileType());
            existing.setPreviewUrl(newItem.getPreviewUrl());
        }

        existing.setUpdatedAt(LocalDateTime.now());

        FormItem saved = repository.save(existing);

        addDepartmentMappingToType(saved.getTypeId(), saved.getDepartmentId());
        removeDepartmentMappingFromOldTypeIfUnused(oldTypeId, oldDepartmentId, saved.getTypeId(), saved.getDepartmentId());

        return saved;
    }

    // ==================== GET BY ID ====================
    public FormItem getById(String id) {
        return repository.findById(id).orElse(null);
    }

    // ==================== GET BY DEPARTMENT ====================
    public List<FormItem> getByDepartment(String departmentId) {
        return repository.findByDepartmentId(departmentId);
    }

    // ==================== GET BY TYPE ====================
    public List<FormItem> getByTypeId(String typeId) {
        return repository.findByTypeId(typeId);
    }

    // ==================== GET ALL ====================
    public List<FormResponse> getAll() {
        return repository.findAll().stream().map(form -> {
            Department dept = departmentService.getById(form.getDepartmentId());

            return toFormResponse(form, dept);
        }).collect(Collectors.toList());
    }

    // ==================== DELETE ====================
    public void delete(String id) {
        FormItem existing = repository.findById(id).orElse(null);

        if (existing == null) {
            return;
        }

        String oldDepartmentId = existing.getDepartmentId();
        String oldTypeId = existing.getTypeId();

        repository.deleteById(id);
        removeDepartmentMappingFromTypeIfUnused(oldTypeId, oldDepartmentId);
    }

    // ==================== SYNC TYPE DEPARTMENTS ====================
    public void syncDepartmentsForType(String typeId) {
        String normalizedTypeId = normalize(typeId);
        if (normalizedTypeId == null) {
            return;
        }

        List<FormItem> forms = repository.findByTypeId(normalizedTypeId);
        Map<String, Department> uniqueDepartments = new LinkedHashMap<>();

        for (FormItem form : forms) {
            if (form == null || normalize(form.getDepartmentId()) == null) {
                continue;
            }

            Department department = departmentService.getById(form.getDepartmentId().trim());
            if (department != null && normalize(department.getId()) != null) {
                uniqueDepartments.put(department.getId().trim(), department);
            }
        }

        documentTypeService.replaceDepartments(normalizedTypeId, new ArrayList<>(uniqueDepartments.values()));
    }

    public void syncDepartmentsForAllTypes() {
        documentTypeService.getAll().forEach(type -> syncDepartmentsForType(type.getId()));
    }

    // ==================== SEARCH ====================
    public Page<FormResponse> search(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String description,
            String typeId,
            Pageable pageable
    ) {
        String normalizedDepartmentId = normalize(departmentId);
        String normalizedDivision = normalize(division);
        String normalizedDepartmentName = normalize(departmentName);
        String normalizedTitle = normalize(title);
        String normalizedDescription = normalize(description);
        String normalizedTypeId = normalize(typeId);

        List<String> effectiveDeptIds = null;

        if (normalizedDivision != null || normalizedDepartmentName != null) {
            List<Department> matchedDepartments = departmentService.getAll().stream()
                    .filter(dept -> containsIgnoreCase(dept.getDivision(), normalizedDivision))
                    .filter(dept -> containsIgnoreCase(dept.getDepartmentName(), normalizedDepartmentName))
                    .collect(Collectors.toList());

            if (matchedDepartments.isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }

            effectiveDeptIds = matchedDepartments.stream()
                    .map(Department::getId)
                    .collect(Collectors.toList());
        }

        if (normalizedDepartmentId != null) {
            Department dept = departmentService.getById(normalizedDepartmentId);

            if (dept == null) {
                return new PageImpl<>(List.of(), pageable, 0);
            }

            if (effectiveDeptIds != null && !effectiveDeptIds.contains(normalizedDepartmentId)) {
                return new PageImpl<>(List.of(), pageable, 0);
            }

            effectiveDeptIds = List.of(normalizedDepartmentId);
        }

        List<Criteria> criteriaList = new ArrayList<>();

        if (effectiveDeptIds != null && !effectiveDeptIds.isEmpty()) {
            criteriaList.add(Criteria.where("departmentId").in(effectiveDeptIds));
        }

        if (normalizedTypeId != null) {
            criteriaList.add(Criteria.where("typeId").is(normalizedTypeId));
        }

        if (normalizedTitle != null) {
            criteriaList.add(Criteria.where("title").regex(normalizedTitle, "i"));
        }

        if (normalizedDescription != null) {
            criteriaList.add(Criteria.where("description").regex(normalizedDescription, "i"));
        }

        Query query = new Query();

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, FormItem.class);

        query.with(pageable);

        List<FormItem> forms = mongoTemplate.find(query, FormItem.class);

        List<FormResponse> content = forms.stream().map(form -> {
            Department dept = departmentService.getById(form.getDepartmentId());

            return toFormResponse(form, dept);
        }).collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    private void addDepartmentMappingToType(String typeId, String departmentId) {
        String normalizedTypeId = normalize(typeId);
        String normalizedDepartmentId = normalize(departmentId);

        if (normalizedTypeId == null || normalizedDepartmentId == null) {
            return;
        }

        Department department = departmentService.getById(normalizedDepartmentId);
        documentTypeService.addDepartmentToType(normalizedTypeId, department);
    }

    private void removeDepartmentMappingFromOldTypeIfUnused(
            String oldTypeId,
            String oldDepartmentId,
            String newTypeId,
            String newDepartmentId
    ) {
        String normalizedOldTypeId = normalize(oldTypeId);
        String normalizedOldDepartmentId = normalize(oldDepartmentId);
        String normalizedNewTypeId = normalize(newTypeId);
        String normalizedNewDepartmentId = normalize(newDepartmentId);

        if (normalizedOldTypeId == null || normalizedOldDepartmentId == null) {
            return;
        }

        boolean sameDepartmentAndType = normalizedOldTypeId.equals(normalizedNewTypeId)
                && normalizedOldDepartmentId.equals(normalizedNewDepartmentId);

        if (sameDepartmentAndType) {
            return;
        }

        removeDepartmentMappingFromTypeIfUnused(normalizedOldTypeId, normalizedOldDepartmentId);
    }

    private void removeDepartmentMappingFromTypeIfUnused(String typeId, String departmentId) {
        String normalizedTypeId = normalize(typeId);
        String normalizedDepartmentId = normalize(departmentId);

        if (normalizedTypeId == null || normalizedDepartmentId == null) {
            return;
        }

        boolean stillUsed = repository.existsByDepartmentIdAndTypeId(normalizedDepartmentId, normalizedTypeId);

        if (!stillUsed) {
            documentTypeService.removeDepartmentFromType(normalizedTypeId, normalizedDepartmentId);
        }
    }

    private FormResponse toFormResponse(FormItem form, Department dept) {
        return new FormResponse(
                form.getId(),
                form.getTitle(),
                form.getDescription(),
                form.getTypeId(),
                form.getFileType(),
                form.getFileUrl(),
                form.getPreviewUrl(),
                form.getDepartmentId(),
                dept != null ? dept.getDepartmentName() : null,
                dept != null ? dept.getDivision() : null,
                form.getCreatedAt(),
                form.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (keyword == null) {
            return true;
        }

        if (source == null) {
            return false;
        }

        return source.toLowerCase().contains(keyword.toLowerCase());
    }

    public boolean existsByDepartmentId(String departmentId) {
        return repository.existsByDepartmentId(departmentId);
    }

    public boolean existsByTypeId(String typeId) {
        return repository.existsByTypeId(typeId);
    }
}
