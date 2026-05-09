package org.bsl.portal.service;

import org.bsl.portal.model.AppLink;
import org.bsl.portal.repository.AppLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AppLinkService {

    @Autowired
    private AppLinkRepository repository;

    // ===============================
    // CREATE - OLD VERSION
    // ===============================
    public AppLink create(String name, String url, String icon, String desc) {
        AppLink link = new AppLink();
        link.setName(name);
        link.setUrl(url);
        link.setIcon(icon);
        link.setDesc(desc);
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());

        return repository.save(link);
    }

    // ===============================
    // CREATE - WITH DEPARTMENT
    // ===============================
    public AppLink create(
            String name,
            String url,
            String icon,
            String desc,
            String departmentId
    ) {
        AppLink link = new AppLink();
        link.setName(name);
        link.setUrl(url);
        link.setIcon(icon);
        link.setDesc(desc);
        link.setDepartmentId(departmentId);
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());

        return repository.save(link);
    }

    // ===============================
    // UPDATE - OLD VERSION
    // ===============================
    public AppLink update(String id, String name, String url, String icon, String desc) {
        Optional<AppLink> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        AppLink link = optional.get();

        link.setName(name);
        link.setUrl(url);
        link.setIcon(icon);
        link.setDesc(desc);
        link.setUpdatedAt(LocalDateTime.now());

        return repository.save(link);
    }

    // ===============================
    // UPDATE - WITH DEPARTMENT
    // ===============================
    public AppLink update(
            String id,
            String name,
            String url,
            String icon,
            String desc,
            String departmentId
    ) {
        Optional<AppLink> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        AppLink link = optional.get();

        link.setName(name);
        link.setUrl(url);
        link.setIcon(icon);
        link.setDesc(desc);
        link.setDepartmentId(departmentId);
        link.setUpdatedAt(LocalDateTime.now());

        return repository.save(link);
    }

    // ===============================
    // DELETE
    // ===============================
    public void delete(String id) {
        repository.deleteById(id);
    }

    // ===============================
    // GET BY ID
    // ===============================
    public AppLink getById(String id) {
        return repository.findById(id).orElse(null);
    }

    // ===============================
    // GET ALL PAGED
    // ===============================
    public Page<AppLink> getAllPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return repository.findAll(pageable);
    }

    // ===============================
    // EXISTS BY NAME
    // ===============================
    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }

    // ===============================
    // SEARCH WITHOUT DEPARTMENT FILTER
    // ===============================
    public Page<AppLink> getAllPagedWithSearch(String name, String desc, Pageable pageable) {
        String n = normalize(name);
        String d = normalize(desc);

        if (n.isEmpty() && d.isEmpty()) {
            return repository.findAll(pageable);
        }

        if (!n.isEmpty() && d.isEmpty()) {
            return repository.findByNameContainingIgnoreCase(n, pageable);
        }

        if (n.isEmpty() && !d.isEmpty()) {
            return repository.findByDescContainingIgnoreCase(d, pageable);
        }

        return repository.findByNameContainingIgnoreCaseAndDescContainingIgnoreCase(n, d, pageable);
    }

    // ===============================
    // SEARCH WITH DEPARTMENT FILTER
    // ===============================
    public Page<AppLink> getAllPagedWithSearch(
            String name,
            String desc,
            String departmentId,
            Pageable pageable
    ) {
        String n = normalize(name);
        String d = normalize(desc);
        String deptId = normalize(departmentId);

        if (deptId.isEmpty()) {
            return Page.empty(pageable);
        }

        if (n.isEmpty() && d.isEmpty()) {
            return repository.findByDepartmentId(deptId, pageable);
        }

        if (!n.isEmpty() && d.isEmpty()) {
            return repository.findByDepartmentIdAndNameContainingIgnoreCase(deptId, n, pageable);
        }

        if (n.isEmpty() && !d.isEmpty()) {
            return repository.findByDepartmentIdAndDescContainingIgnoreCase(deptId, d, pageable);
        }

        return repository.findByDepartmentIdAndNameContainingIgnoreCaseAndDescContainingIgnoreCase(
                deptId,
                n,
                d,
                pageable
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean existsByDepartmentId(String departmentId) {
        return repository.existsByDepartmentId(departmentId);
    }
}