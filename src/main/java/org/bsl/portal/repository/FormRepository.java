package org.bsl.portal.repository;

import org.bsl.portal.model.FormItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;           // ← import đúng cái này
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FormRepository extends MongoRepository<FormItem, String> {

    boolean existsByTitleAndDepartmentId(String title, String departmentId);

    List<FormItem> findByDepartmentId(String departmentId);

    // Query tìm kiếm + phân trang + sort động
    @Query("{ $and: [ " +
            "  ?#{ [0] == null || [0].isEmpty() ? {} : { 'departmentId': [0] } }, " +
            "  ?#{ [1] == null || [1].isEmpty() ? {} : { 'title': { $regex: [1], $options: 'i' } } }, " +
            "  ?#{ [2] == null || [2].isEmpty() ? {} : { 'description': { $regex: [2], $options: 'i' } } } " +
            "] }")
    Page<FormItem> searchForms(String departmentId, String title, String description, Pageable pageable);
}