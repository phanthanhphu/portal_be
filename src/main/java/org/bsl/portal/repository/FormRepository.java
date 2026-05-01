package org.bsl.portal.repository;

import org.bsl.portal.model.FormItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FormRepository extends MongoRepository<FormItem, String> {

    boolean existsByTitleAndDepartmentId(String title, String departmentId);

    boolean existsByTitleAndDepartmentIdAndTypeId(String title, String departmentId, String typeId);

    boolean existsByDepartmentIdAndTypeId(String departmentId, String typeId);

    List<FormItem> findByDepartmentId(String departmentId);

    List<FormItem> findByTypeId(String typeId);

    List<FormItem> findByDepartmentIdAndTypeId(String departmentId, String typeId);

    // Search form/document:
    // - departmentId rỗng/null thì bỏ qua department
    // - typeId rỗng/null thì bỏ qua type
    // - title rỗng/null thì bỏ qua title
    // - description rỗng/null thì bỏ qua description
    @Query("{ $and: [ " +
            "  ?#{ [0] == null || [0].trim().isEmpty() ? {} : { 'departmentId': [0].trim() } }, " +
            "  ?#{ [1] == null || [1].trim().isEmpty() ? {} : { 'typeId': [1].trim() } }, " +
            "  ?#{ [2] == null || [2].trim().isEmpty() ? {} : { 'title': { $regex: [2].trim(), $options: 'i' } } }, " +
            "  ?#{ [3] == null || [3].trim().isEmpty() ? {} : { 'description': { $regex: [3].trim(), $options: 'i' } } } " +
            "] }")
    Page<FormItem> searchForms(
            String departmentId,
            String typeId,
            String title,
            String description,
            Pageable pageable
    );
}
