package org.bsl.portal.repository;

import org.bsl.portal.model.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends MongoRepository<DocumentType, String> {

    boolean existsByNameIgnoreCase(String name);

    Optional<DocumentType> findByNameIgnoreCase(String name);

    boolean existsByDepartmentsContaining(String departmentId);

    // SEARCH CÓ PHÂN TRANG
    Page<DocumentType> findByNameContainingIgnoreCase(String name, Pageable pageable);
}