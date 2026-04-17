package org.bsl.portal.repository;

import org.bsl.portal.model.DepartmentForm;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DepartmentFormRepository extends MongoRepository<DepartmentForm, String> {

    boolean existsByDepartmentName(String departmentName);

}