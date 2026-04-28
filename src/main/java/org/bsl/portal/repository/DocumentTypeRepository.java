package org.bsl.portal.repository;

import org.bsl.portal.model.DocumentType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends MongoRepository<DocumentType, String> {

    boolean existsByNameIgnoreCase(String name);

    Optional<DocumentType> findByNameIgnoreCase(String name);

    List<DocumentType> findByNameContainingIgnoreCase(String name);
}