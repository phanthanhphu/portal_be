package org.bsl.portal.service;

import org.bsl.portal.model.AppLink;
import org.bsl.portal.repository.AppLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AppLinkService {

    @Autowired
    private AppLinkRepository repository;

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

    public void delete(String id) {
        repository.deleteById(id);
    }

    public AppLink getById(String id) {
        return repository.findById(id).orElse(null);
    }

    public Page<AppLink> getAllPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return repository.findAll(pageable);
    }

    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }


    public Page<AppLink> getAllPagedWithSearch(String name, String desc, Pageable pageable) {
        // Trim và xử lý null an toàn
        String n = (name == null) ? "" : name.trim();
        String d = (desc == null) ? "" : desc.trim();

        // Trường hợp không có từ khóa tìm kiếm nào
        if (n.isEmpty() && d.isEmpty()) {
            return repository.findAll(pageable);
        }

        // Chỉ tìm theo name
        if (!n.isEmpty() && d.isEmpty()) {
            return repository.findByNameContainingIgnoreCase(n, pageable);
        }

        // Chỉ tìm theo description
        if (n.isEmpty() && !d.isEmpty()) {
            return repository.findByDescContainingIgnoreCase(d, pageable);
        }

        // Tìm theo cả name AND description
        return repository.findByNameContainingIgnoreCaseAndDescContainingIgnoreCase(n, d, pageable);
    }
}