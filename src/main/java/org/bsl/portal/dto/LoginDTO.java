package org.bsl.portal.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Login request shared by Domain and system-account authentication.
 *
 * Backward compatibility:
 * - New clients should send "identifier".
 * - Older clients may continue sending "email".
 * - When loginType is omitted, DOMAIN is used because Domain login is the default option.
 */
public class LoginDTO {

    private String identifier;
    private String email;

    @NotBlank(message = "Password cannot be blank")
    private String password;

    private String loginType = "DOMAIN";

    public LoginDTO() {
    }

    public LoginDTO(String identifier, String password, String loginType) {
        this.identifier = identifier;
        this.password = password;
        this.loginType = loginType;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String resolveIdentifier() {
        if (identifier != null && !identifier.trim().isEmpty()) {
            return identifier.trim();
        }

        return email == null ? "" : email.trim();
    }
}
