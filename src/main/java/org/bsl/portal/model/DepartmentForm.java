package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "department_forms")
public class DepartmentForm {

    @Id
    private String id;

    private String division;        // Ví dụ: ADMIN, SALES, HR
    private String departmentName;  // Ví dụ: Phòng Nhân sự

    private List<FormItem> forms;

    @Indexed
    private LocalDateTime createdAt;

    @Indexed
    private LocalDateTime updatedAt;

    public DepartmentForm() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public DepartmentForm(String id, String division, String departmentName, List<FormItem> forms) {
        this.id = id;
        this.division = division;
        this.departmentName = departmentName;
        this.forms = forms;

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public List<FormItem> getForms() {
        return forms;
    }

    public void setForms(List<FormItem> forms) {
        this.forms = forms;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}