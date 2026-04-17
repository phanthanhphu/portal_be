package org.bsl.portal.service;

import org.bsl.portal.model.DepartmentForm;
import org.bsl.portal.model.FormItem;
import org.bsl.portal.repository.DepartmentFormRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DepartmentFormService {

    @Autowired
    private DepartmentFormRepository repository;

    public DepartmentForm create(String division, String departmentName) {

        DepartmentForm form = new DepartmentForm();
        form.setDivision(division);
        form.setDepartmentName(departmentName);
        form.setForms(new ArrayList<>());
        form.setCreatedAt(LocalDateTime.now());
        form.setUpdatedAt(LocalDateTime.now());

        return repository.save(form);
    }

    public DepartmentForm update(String id, String division, String departmentName) {

        Optional<DepartmentForm> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        DepartmentForm form = optional.get();
        form.setDivision(division);
        form.setDepartmentName(departmentName);
        form.setUpdatedAt(LocalDateTime.now());

        return repository.save(form);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public DepartmentForm getById(String id) {
        return repository.findById(id).orElse(null);
    }

    public List<DepartmentForm> getAll() {
        return repository.findAll();
    }

    public boolean existsByDepartmentName(String name) {
        return repository.existsByDepartmentName(name);
    }

    public DepartmentForm addFormItem(String departmentId, FormItem item) {

        DepartmentForm department = repository.findById(departmentId).orElse(null);

        if (department == null) {
            return null;
        }

        item.setId(UUID.randomUUID().toString());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        department.getForms().add(item);
        department.setUpdatedAt(LocalDateTime.now());

        return repository.save(department);
    }

    public DepartmentForm removeFormItem(String departmentId, String formId) {

        DepartmentForm department = repository.findById(departmentId).orElse(null);

        if (department == null) {
            return null;
        }

        department.getForms().removeIf(f -> f.getId().equals(formId));
        department.setUpdatedAt(LocalDateTime.now());

        return repository.save(department);
    }

    public DepartmentForm updateFormItem(String departmentId, String formId, FormItem item) {

        DepartmentForm department = repository.findById(departmentId).orElse(null);

        if (department == null) {
            return null;
        }

        for (FormItem form : department.getForms()) {

            if (form.getId().equals(formId)) {

                form.setTitle(item.getTitle());
                form.setFileUrl(item.getFileUrl());
                form.setUpdatedAt(LocalDateTime.now());

                department.setUpdatedAt(LocalDateTime.now());

                return repository.save(department);
            }
        }

        return null;
    }
}