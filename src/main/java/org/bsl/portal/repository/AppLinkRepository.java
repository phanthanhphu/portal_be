package org.bsl.portal.repository;

import org.bsl.portal.model.AppLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AppLinkRepository extends MongoRepository<AppLink, String> {

    boolean existsByName(String name);

    // Search for AppLinks by name and description - old method nếu chỗ khác còn dùng
    List<AppLink> findByNameContainingIgnoreCaseAndDescContainingIgnoreCase(
            String name,
            String desc
    );

    Page<AppLink> findByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );

    Page<AppLink> findByDescContainingIgnoreCase(
            String desc,
            Pageable pageable
    );

    Page<AppLink> findByNameContainingIgnoreCaseAndDescContainingIgnoreCase(
            String name,
            String desc,
            Pageable pageable
    );

    // ===============================
    // SEARCH WITH DEPARTMENT FILTER
    // ===============================

    Page<AppLink> findByDepartmentId(
            String departmentId,
            Pageable pageable
    );

    Page<AppLink> findByDepartmentIdAndNameContainingIgnoreCase(
            String departmentId,
            String name,
            Pageable pageable
    );

    Page<AppLink> findByDepartmentIdAndDescContainingIgnoreCase(
            String departmentId,
            String desc,
            Pageable pageable
    );

    Page<AppLink> findByDepartmentIdAndNameContainingIgnoreCaseAndDescContainingIgnoreCase(
            String departmentId,
            String name,
            String desc,
            Pageable pageable
    );

    boolean existsByDepartmentId(String departmentId);
}