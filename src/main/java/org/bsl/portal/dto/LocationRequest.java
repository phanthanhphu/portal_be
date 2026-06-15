package org.bsl.portal.dto;

public class LocationRequest {

    private String location;

    // User ID người tạo. Dùng khi create.
    private String userIdCreate;

    public LocationRequest() {
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUserIdCreate() {
        return userIdCreate;
    }

    public void setUserIdCreate(String userIdCreate) {
        this.userIdCreate = userIdCreate;
    }
}
