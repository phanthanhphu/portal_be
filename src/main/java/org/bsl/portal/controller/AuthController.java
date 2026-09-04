package org.bsl.portal.controller;

import org.bsl.portal.dto.LoginDTO;
import org.bsl.portal.exception.LoginFailureException;
import org.bsl.portal.model.User;
import org.bsl.portal.security.JwtUtil;
import org.bsl.portal.service.ActiveDirectoryService;
import org.bsl.portal.service.LoginAuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compatibility login endpoint. The main UI uses /api/users/login, but this
 * endpoint follows the same Domain/System authentication rules.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginAuthenticationService loginAuthenticationService;
    private final JwtUtil jwtUtil;

    public AuthController(LoginAuthenticationService loginAuthenticationService, JwtUtil jwtUtil) {
        this.loginAuthenticationService = loginAuthenticationService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginDTO loginRequest) {
        try {
            LoginAuthenticationService.LoginResult loginResult = loginAuthenticationService.authenticate(loginRequest);
            User user = loginResult.getUser();
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getTokenVersion());

            Map<String, Object> safeUser = new LinkedHashMap<>();
            safeUser.put("id", user.getId());
            safeUser.put("username", user.getUsername());
            safeUser.put("email", user.getEmail());
            safeUser.put("role", user.getRole());
            safeUser.put("enabled", user.isEnabled());
            safeUser.put("departmentId", user.getDepartmentId());
            safeUser.put("approvePermission", user.getApprovePermission());
            safeUser.put("bookingPermission", user.getBookingPermission());
            safeUser.put("modulePermission", user.getModulePermission());
            safeUser.put("canApproveNotice", user.canApproveNotice());
            safeUser.put("canApproveDocument", user.canApproveDocument());
            safeUser.put("canManageBooking", user.canManageBooking());
            safeUser.put("canManageAppLinks", user.canManageAppLinks());
            safeUser.put("canManageNotice", user.canManageNotice());
            safeUser.put("canManageDocument", user.canManageDocument());
            safeUser.put("canManageDepartment", user.canManageDepartment());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", loginResult.getLoginType().name() + " login successful");
            response.put("token", token);
            response.put("authenticationType", loginResult.getLoginType().name());
            response.put("user", safeUser);

            ActiveDirectoryService.DirectoryUser directoryUser = loginResult.getDirectoryUser();
            if (directoryUser != null) {
                Map<String, Object> domainUser = new LinkedHashMap<>();
                domainUser.put("username", directoryUser.getUsername());
                domainUser.put("userPrincipalName", directoryUser.getUserPrincipalName());
                domainUser.put("email", directoryUser.getEmail());
                domainUser.put("displayName", directoryUser.getDisplayName());
                response.put("domainUser", domainUser);
            }

            return ResponseEntity.ok(response);
        } catch (LoginFailureException ex) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", ex.getMessage());
            response.put("code", ex.getCode());
            return ResponseEntity.status(ex.getStatus()).body(response);
        } catch (Exception ex) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "An unexpected error occurred while signing in.");
            response.put("code", "LOGIN_UNEXPECTED_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
