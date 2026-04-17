package org.bsl.portal.repository;

import org.bsl.portal.model.AppLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AppLinkRepository extends MongoRepository<AppLink, String> {

    boolean existsByName(String name);
    // Search for AppLinks by name and description (case insensitive)
    List<AppLink> findByNameContainingIgnoreCaseAndDescContainingIgnoreCase(String name, String desc);

    Page<AppLink> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<AppLink> findByDescContainingIgnoreCase(String desc, Pageable pageable);

    Page<AppLink> findByNameContainingIgnoreCaseAndDescContainingIgnoreCase(
            String name, String desc, Pageable pageable);
}