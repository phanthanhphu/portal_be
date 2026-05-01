package org.bsl.portal.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "document_types")
public class DocumentType {

    @Id
    private String id;

    private String name;

    private List<DocumentTypeDepartment> departments = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public DocumentType() {
    }

    public DocumentType(
            String id,
            String name,
            List<DocumentTypeDepartment> departments,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.departments = departments != null ? departments : new ArrayList<>();
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<DocumentTypeDepartment> getDepartments() {
        return departments;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDepartments(List<DocumentTypeDepartment> departments) {
        this.departments = departments != null ? departments : new ArrayList<>();
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
