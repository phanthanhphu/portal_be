package org.bsl.portal.service;

import org.bsl.portal.model.DocumentType;
import org.bsl.portal.repository.DocumentTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
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
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    // ==================== DELETE ====================
    public void delete(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Document type ID is required");
        }

        if (!repository.existsById(id)) {
            throw new NoSuchElementException("Document type not found");
        }

        repository.deleteById(id);
    }

    // ==================== GET ALL ====================
    public List<DocumentType> getAll() {
        return repository.findAll();
    }

    // ==================== GET BY ID ====================
    public DocumentType getById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }

        return repository.findById(id).orElse(null);
    }

    // ==================== SEARCH BY NAME ====================
    public List<DocumentType> searchByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return repository.findAll();
        }

        return repository.findByNameContainingIgnoreCase(name.trim());
    }

    private void validateName(DocumentType type) {
        if (type == null) {
            throw new IllegalArgumentException("Document type body is required");
        }

        if (type.getName() == null || type.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Document type name is required");
        }
    }
}