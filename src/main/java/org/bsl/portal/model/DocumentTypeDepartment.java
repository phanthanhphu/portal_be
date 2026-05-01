package org.bsl.portal.model;

public class DocumentTypeDepartment {

    private String idDepartment;

    private String name;

    public DocumentTypeDepartment() {
    }

    public DocumentTypeDepartment(String idDepartment, String name) {
        this.idDepartment = idDepartment;
        this.name = name;
    }

    public String getIdDepartment() {
        return idDepartment;
    }

    public void setIdDepartment(String idDepartment) {
        this.idDepartment = idDepartment;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
