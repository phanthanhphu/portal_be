package org.bsl.portal.service;

import org.bsl.portal.dto.FormResponse;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.FormItem;
import org.bsl.portal.repository.FormRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private MongoTemplate mongoTemplate;

    // ==================== CHECK TITLE TRÙNG TRONG CÙNG DEPARTMENT ====================
    public boolean existsByTitleAndDepartmentId(String title, String departmentId) {
        return repository.existsByTitleAndDepartmentId(title, departmentId);
    }

    // ==================== CREATE ====================
    public FormItem create(FormItem item) {
        // Nếu id chưa có thì tạo mới
        if (item.getId() == null || item.getId().isEmpty()) {
            item.setId(UUID.randomUUID().toString());
        }

        LocalDateTime now = LocalDateTime.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return repository.save(item);
    }

    // ==================== UPDATE ====================
    public FormItem update(String id, FormItem newItem) {
        Optional<FormItem> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        FormItem existing = optional.get();

        // Cập nhật các trường cơ bản
        existing.setTitle(newItem.getTitle());
        existing.setDescription(newItem.getDescription());
        existing.setDepartmentId(newItem.getDepartmentId());

        // Chỉ cập nhật fileUrl nếu có file mới được upload
        if (newItem.getFileUrl() != null && !newItem.getFileUrl().isEmpty()) {
            existing.setFileUrl(newItem.getFileUrl());
            existing.setFileType(newItem.getFileType());
            existing.setPreviewUrl(newItem.getPreviewUrl());
        }

        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    // ==================== GET BY ID ====================
    public FormItem getById(String id) {
        return repository.findById(id).orElse(null);
    }

    // ==================== GET BY DEPARTMENT ====================
    public List<FormItem> getByDepartment(String departmentId) {
        return repository.findByDepartmentId(departmentId);
    }

    // ==================== GET ALL ====================
    public List<FormResponse> getAll() {
        return repository.findAll().stream().map(form -> {
            Department dept = departmentService.getById(form.getDepartmentId());

            return new FormResponse(
                    form.getId(),
                    form.getTitle(),
                    form.getDescription(),
                    form.getFileType(),
                    form.getFileUrl(),
                    form.getPreviewUrl(),
                    form.getDepartmentId(),
                    dept != null ? dept.getDepartmentName() : null,
                    dept != null ? dept.getDivision() : null,
                    form.getCreatedAt(),
                    form.getUpdatedAt()
            );
        }).collect(Collectors.toList());
    }

    // ==================== DELETE ====================
    public void delete(String id) {
        repository.deleteById(id);
    }


    public Page<FormResponse> search(
            String division,
            String departmentName,
            String title,
            String description,
            Pageable pageable) {

        String normalizedDivision = normalize(division);
        String normalizedDepartmentName = normalize(departmentName);
        String normalizedTitle = normalize(title);
        String normalizedDescription = normalize(description);

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

        List<Criteria> criteriaList = new ArrayList<>();

        if (effectiveDeptIds != null && !effectiveDeptIds.isEmpty()) {
            criteriaList.add(Criteria.where("departmentId").in(effectiveDeptIds));
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
            return new FormResponse(
                    form.getId(),
                    form.getTitle(),
                    form.getDescription(),
                    form.getFileType(),
                    form.getFileUrl(),
                    form.getPreviewUrl(),
                    form.getDepartmentId(),
                    dept != null ? dept.getDepartmentName() : null,
                    dept != null ? dept.getDivision() : null,
                    form.getCreatedAt(),
                    form.getUpdatedAt()
            );
        }).collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (keyword == null) return true;
        if (source == null) return false;
        return source.toLowerCase().contains(keyword.toLowerCase());
    }
}