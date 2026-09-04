package org.bsl.portal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.bsl.portal.common.socket.AppSocketPublisher;
import org.bsl.portal.dto.ChangePasswordDTO;
import org.bsl.portal.dto.LoginDTO;
import org.bsl.portal.dto.ResetPasswordDTO;
import org.bsl.portal.dto.UserDTO;
import org.bsl.portal.exception.LoginFailureException;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.User;
import org.bsl.portal.repository.UserRepository;
import org.bsl.portal.request.UserRequest;
import org.bsl.portal.security.JwtUtil;
import org.bsl.portal.service.ActiveDirectoryService;
import org.bsl.portal.service.DepartmentService;
import org.bsl.portal.service.LoginAuthenticationService;
import org.bsl.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginAuthenticationService loginAuthenticationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private AppSocketPublisher appSocketPublisher;

    // 🔥 SWAGGER TOKENS - IN-MEMORY MAP!
    private final Map<String, String> swaggerTokens = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private static final String UPLOAD_DIR = "uploads/users/";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    public static final String PERMISSION_NONE = "NONE";
    public static final String PERMISSION_NOTICE = "NOTICE";
    public static final String PERMISSION_DOCUMENT = "DOCUMENT";
    public static final String PERMISSION_APP_LINK = "APP_LINK";
    public static final String PERMISSION_BOTH_ALIAS = "BOTH";

    public static final String APPROVE_NONE = PERMISSION_NONE;
    public static final String APPROVE_NOTICE = PERMISSION_NOTICE;
    public static final String APPROVE_DOCUMENT = PERMISSION_DOCUMENT;
    public static final String APPROVE_BOTH = PERMISSION_BOTH_ALIAS;

    public static final String BOOKING_NONE = "NONE";
    public static final String BOOKING_MANAGE = "BOOKING";

    // Set để lưu các token đã bị blacklist (để invalidate token khi logout)
    private final Set<String> blacklistedTokens = new HashSet<>();

    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create a new user with optional profile image and required department",
            description = "Create a new user with user data (form parameters), required departmentId, and an optional profile image using multipart/form-data.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = UserRequest.class)
                    )
            )
    )
    public ResponseEntity<Map<String, Object>> addUser(@ModelAttribute UserRequest request) {
        try {
            logger.info("Received user data: username={}, email={}, password={}, address={}, phone={}, role={}, isEnabled={}, departmentId={}",
                    request.getUsername(), request.getEmail(), request.getPassword(),
                    request.getAddress(), request.getPhone(), request.getRole(),
                    request.getIsEnabled(), request.getDepartmentId());

            logger.info("Received profileImage: {}",
                    request.getProfileImage() != null ? request.getProfileImage().getOriginalFilename() : "null");

            // Validate required fields
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Username cannot be empty"));
            }

            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Email cannot be empty"));
            }

            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Password cannot be empty"));
            }

            if (request.getDepartmentId() == null || request.getDepartmentId().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Department is required"));
            }

            // Construct User object
            User user = new User();
            user.setUsername(request.getUsername());
            user.setEmail(request.getEmail());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setAddress(request.getAddress());
            user.setPhone(request.getPhone());
            user.setRole(request.getRole());
            user.setTokenVersion(1L);
            user.setDepartmentId(request.getDepartmentId());
            user.setApprovePermission(normalizeApprovePermission(request.getApprovePermission()));
            user.setBookingPermission(normalizeBookingPermission(request.getBookingPermission()));
            user.setModulePermission(normalizeModulePermission(request.getModulePermission()));

            Boolean isEnabled = request.getIsEnabled();
            user.setEnabled(isEnabled != null ? isEnabled : true);
            logger.info("User enabled status set to: {}", user.isEnabled());

            // Check duplicate email
            if (userService.findByEmail(user.getEmail()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "User with this email already exists"));
            }

            // Handle image upload
            String profileImageUrl = null;
            MultipartFile profileImage = request.getProfileImage();
            if (profileImage != null && !profileImage.isEmpty()) {
                profileImageUrl = saveProfileImage(profileImage);
                logger.info("Profile image saved at: {}", profileImageUrl);
                user.setProfileImageUrl(profileImageUrl);
            }

            user.setId(UUID.randomUUID().toString());
            user.setCreatedAt(LocalDateTime.now());

            User savedUser = userService.saveUser(user);

            appSocketPublisher.userChanged("CREATED", savedUser.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "User created successfully");
            response.put("data", buildUserResponse(savedUser));
            if (profileImageUrl != null) {
                response.put("profileImageUrl", profileImageUrl);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            logger.error("Error processing file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Error processing file: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in addUser: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error: " + e.getMessage()));
        }
    }

    private String saveProfileImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IOException("Image file size exceeds limit of 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !Arrays.asList("image/jpeg", "image/png", "image/gif").contains(contentType)) {
            throw new IOException("Only JPEG, PNG, and GIF files are allowed");
        }

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path path = Paths.get(UPLOAD_DIR + fileName);

        Files.createDirectories(path.getParent());

        try {
            file.transferTo(path);
            logger.info("Saved file to: {}", path.toString());
        } catch (IOException e) {
            logger.error("Failed to save file: {}", e.getMessage(), e);
            throw new IOException("Failed to save profile image: " + file.getOriginalFilename(), e);
        }

        return "/uploads/users/" + fileName;
    }

    @GetMapping
    @Operation(
            summary = "Filter users",
            description = "Retrieve a paginated list of users filtered by the provided criteria."
    )
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "") String username,
            @RequestParam(required = false, defaultValue = "") String address,
            @RequestParam(required = false, defaultValue = "") String phone,
            @RequestParam(required = false, defaultValue = "") String email,
            @RequestParam(required = false, defaultValue = "") String role,
            @RequestParam(required = false, defaultValue = "") String approvePermission,
            @RequestParam(required = false, defaultValue = "") String bookingPermission
    ) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
            Page<UserDTO> userDTOPage = userService.filterUsers(
                    username,
                    address,
                    phone,
                    email,
                    role,
                    normalizeApprovePermissionFilter(approvePermission),
                    normalizeBookingPermissionFilter(bookingPermission),
                    pageable
            );

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Users retrieved successfully");
            response.put("users", userDTOPage.getContent());
            response.put("currentPage", userDTOPage.getNumber());
            response.put("totalItems", userDTOPage.getTotalElements());
            response.put("totalPages", userDTOPage.getTotalPages());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to retrieve users: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve a user's details by ID")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable String id) {
        try {
            Optional<User> userOpt = userService.findById(id);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found with ID: " + id));
            }

            User user = userOpt.get();
            return ResponseEntity.ok(Map.of(
                    "message", "User retrieved successfully",
                    "data", buildUserResponse(user)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to retrieve user: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login with Domain or system account",
            description = "Use loginType DOMAIN (default) or SYSTEM. Domain login authenticates with Active Directory, then loads role and permissions from this system database."
    )
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginDTO loginRequest,
                                                     HttpServletRequest request,
                                                     HttpSession session) {
        String identifier = loginRequest != null ? loginRequest.resolveIdentifier() : "";

        try {
            LoginAuthenticationService.LoginResult loginResult = loginAuthenticationService.authenticate(loginRequest);
            User user = loginResult.getUser();

            long tokenVersion = user.getTokenVersion();
            String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), tokenVersion);

            session.setAttribute("swaggerBearerToken", token);
            session.setAttribute("authenticatedSession", true);
            session.setAttribute("authenticationType", loginResult.getLoginType().name());
            session.setMaxInactiveInterval(3600 * 24);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", loginResult.getLoginType().name() + " login successful");
            response.put("token", token);
            response.put("authenticationType", loginResult.getLoginType().name());
            response.put("user", buildUserResponse(user));

            ActiveDirectoryService.DirectoryUser directoryUser = loginResult.getDirectoryUser();
            if (directoryUser != null) {
                Map<String, Object> domainUser = new LinkedHashMap<>();
                domainUser.put("username", directoryUser.getUsername());
                domainUser.put("userPrincipalName", directoryUser.getUserPrincipalName());
                domainUser.put("email", directoryUser.getEmail());
                domainUser.put("displayName", directoryUser.getDisplayName());
                response.put("domainUser", domainUser);
            }

            response.put("autoAuthorize", true);
            response.put("sessionActive", true);
            response.put("sessionTimeout", "24h");

            logger.info(
                    "Login successful: identifier={} authenticationType={} applicationUser={}",
                    identifier,
                    loginResult.getLoginType(),
                    user.getEmail()
            );

            return ResponseEntity.ok(response);
        } catch (LoginFailureException ex) {
            logger.warn(
                    "Login rejected: identifier={} code={} message={}",
                    identifier,
                    ex.getCode(),
                    ex.getMessage()
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", ex.getMessage());
            response.put("code", ex.getCode());
            return ResponseEntity.status(ex.getStatus()).body(response);
        } catch (Exception ex) {
            logger.error("Unexpected login error for identifier={}", identifier, ex);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message", "An unexpected error occurred while signing in. Please contact the administrator.");
            response.put("code", "LOGIN_UNEXPECTED_ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/logout")
    @Operation(
            summary = "LOGOUT - Clear Session",
            description = "Clear session flags and invalidate session. Must login again to access APIs."
    )
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        try {
            session.removeAttribute("authenticatedSession");
            session.removeAttribute("swaggerBearerToken");
            session.removeAttribute("authenticationType");
            session.invalidate();

            logger.info("LOGOUT SUCCESS - Session: {} cleared", session.getId());

            return ResponseEntity.ok(Map.of(
                    "message", "Logout successful",
                    "session", "cleared",
                    "nextStep", "Login again to continue"
            ));
        } catch (Exception e) {
            logger.error("Logout error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Logout failed"));
        }
    }

    @GetMapping("/check-session")
    @Operation(summary = "Check Swagger Session Token", description = "Debug endpoint to check if token exists")
    public ResponseEntity<Map<String, Object>> checkSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        boolean hasToken = session != null && session.getAttribute("swaggerBearerToken") != null;

        Map<String, Object> response = Map.of(
                "success", true,
                "hasToken", hasToken,
                "sessionActive", session != null,
                "autoAuthorize", hasToken ? "✅ READY!" : "❌ LOGIN AGAIN"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/get-swagger-token")
    @Operation(hidden = true)
    public ResponseEntity<String> getSwaggerToken(HttpSession session) {
        String token = (String) session.getAttribute("swaggerBearerToken");
        return ResponseEntity.ok(token != null ? token : "");
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Update an existing user with optional profile image and department",
            description = "Update a user with user data (form parameters), optional departmentId, and an optional profile image using multipart/form-data.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(implementation = UserRequest.class)
                    )
            )
    )
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String id,
            @ModelAttribute UserRequest request) {
        try {
            logger.info("Received user data for update: username={}, email={}, password={}, address={}, phone={}, role={}, departmentId={}",
                    request.getUsername(), request.getEmail(), request.getPassword(),
                    request.getAddress(), request.getPhone(), request.getRole(),
                    request.getDepartmentId());

            logger.info("Received profileImage: {}",
                    request.getProfileImage() != null ? request.getProfileImage().getOriginalFilename() : "null");

            Optional<User> existingUserOpt = userService.findById(id);
            if (existingUserOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found with ID: " + id));
            }

            User existingUser = existingUserOpt.get();

            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Username cannot be empty"));
            }

            // Some older FE builds omit empty multipart fields. During update, keep the
            // existing email instead of failing when the email field is not included.
            String requestedEmail = request.getEmail() != null ? request.getEmail().trim() : "";
            String effectiveEmail = !requestedEmail.isEmpty()
                    ? requestedEmail
                    : (existingUser.getEmail() != null ? existingUser.getEmail().trim() : "");

            if (effectiveEmail.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Email cannot be empty"));
            }

            Optional<User> userWithEmail = userService.findByEmail(effectiveEmail);
            if (userWithEmail.isPresent() && !userWithEmail.get().getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Email is already used by another user"));
            }

            User user = new User();
            user.setId(id);
            user.setUsername(request.getUsername().trim());
            user.setEmail(effectiveEmail);
            user.setPassword(existingUser.getPassword());
            user.setAddress(request.getAddress());
            user.setPhone(request.getPhone());
            user.setRole(request.getRole() != null ? request.getRole() : existingUser.getRole());
            user.setCreatedAt(existingUser.getCreatedAt());
            user.setTokenVersion(existingUser.getTokenVersion());
            user.setDepartmentId(request.getDepartmentId());
            user.setApprovePermission(
                    request.getApprovePermission() != null
                            ? normalizeApprovePermission(request.getApprovePermission())
                            : normalizeApprovePermission(existingUser.getApprovePermission())
            );
            user.setBookingPermission(
                    request.getBookingPermission() != null
                            ? normalizeBookingPermission(request.getBookingPermission())
                            : normalizeBookingPermission(existingUser.getBookingPermission())
            );
            user.setModulePermission(
                    request.getModulePermission() != null
                            ? normalizeModulePermission(request.getModulePermission())
                            : normalizeModulePermission(existingUser.getModulePermission())
            );

            Boolean isEnabled = request.getIsEnabled();
            user.setEnabled(isEnabled != null ? isEnabled : existingUser.isEnabled());

            String profileImageUrl = null;
            MultipartFile profileImage = request.getProfileImage();
            if (profileImage != null && !profileImage.isEmpty()) {
                if (existingUser.getProfileImageUrl() != null) {
                    String oldImagePath = UPLOAD_DIR + existingUser.getProfileImageUrl().replace("/uploads/users/", "");
                    try {
                        Files.deleteIfExists(Paths.get(oldImagePath));
                        logger.info("Deleted old profile image: {}", oldImagePath);
                    } catch (IOException e) {
                        logger.warn("Failed to delete old profile image: {}", oldImagePath, e);
                    }
                }

                profileImageUrl = saveProfileImage(profileImage);
                logger.info("Profile image saved at: {}", profileImageUrl);
                user.setProfileImageUrl(profileImageUrl);
            } else {
                user.setProfileImageUrl(existingUser.getProfileImageUrl());
            }

            User updatedUser = userService.updateUser(id, user);

            appSocketPublisher.userChanged("UPDATED", updatedUser.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "User updated successfully");
            response.put("data", buildUserResponse(updatedUser));

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            logger.error("Error processing file: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Error processing file: " + e.getMessage()));
        } catch (Exception e) {
            logger.error("Unexpected error in updateUser: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Unexpected error: " + e.getMessage()));
        }
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change user password", description = "Change the password WITHOUT authentication + AUTO LOGOUT all sessions")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordDTO passwordRequest) {
        try {
            if (passwordRequest.getEmail() == null || passwordRequest.getEmail().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Email is required"));
            }

            if (passwordRequest.getOldPassword() == null || passwordRequest.getOldPassword().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Old password is required"));
            }

            if (passwordRequest.getNewPassword() == null || passwordRequest.getNewPassword().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "New password is required"));
            }

            if (passwordRequest.getConfirmNewPassword() == null || passwordRequest.getConfirmNewPassword().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Confirm password is required"));
            }

            logger.info("Change password request for email: {}", passwordRequest.getEmail());

            Optional<User> userOptional = userRepository.findByEmail(passwordRequest.getEmail());
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOptional.get();

            if (!passwordEncoder.matches(passwordRequest.getOldPassword(), user.getPassword())) {
                logger.warn("Invalid old password for user: {}", passwordRequest.getEmail());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Invalid old password"));
            }

            if (!passwordRequest.getNewPassword().equals(passwordRequest.getConfirmNewPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "New password and confirm password must match"));
            }

            if (passwordEncoder.matches(passwordRequest.getNewPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "New password must be different from current password"));
            }

            incrementTokenVersionAndBlacklist(user);
            logger.info("Invalidated all existing tokens for user: {}", user.getEmail());

            user.setPassword(passwordEncoder.encode(passwordRequest.getNewPassword()));
            userRepository.save(user);

            appSocketPublisher.userChanged("UPDATED", user.getId());

            Map<String, String> response = new HashMap<>();
            response.put("message", "Password changed successfully");
            response.put("logoutMessage", "All your sessions have been logged out for security. Please login again.");

            logger.info("Password changed successfully for user: {}", user.getEmail());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error changing password for {}: {}", passwordRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to change password: " + e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset user password", description = "Reset the password without requiring old password + AUTO LOGOUT all sessions")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordDTO passwordRequest) {
        try {
            if (passwordRequest.getEmail() == null || passwordRequest.getEmail().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Email is required"));
            }

            if (passwordRequest.getNewPassword() == null || passwordRequest.getNewPassword().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "New password is required"));
            }

            if (passwordRequest.getConfirmNewPassword() == null || passwordRequest.getConfirmNewPassword().isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Confirm password is required"));
            }

            logger.info("Reset password request for email: {}", passwordRequest.getEmail());

            Optional<User> userOptional = userRepository.findByEmail(passwordRequest.getEmail());
            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found"));
            }

            User user = userOptional.get();

            if (!passwordRequest.getNewPassword().equals(passwordRequest.getConfirmNewPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "New password and confirm password must match"));
            }

            if (passwordEncoder.matches(passwordRequest.getNewPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "New password must be different from current password"));
            }

            incrementTokenVersionAndBlacklist(user);
            user.setPassword(passwordEncoder.encode(passwordRequest.getNewPassword()));
            userRepository.save(user);

            appSocketPublisher.userChanged("UPDATED", user.getId());

            logger.info("Invalidated all tokens and sessions for user: {}", user.getEmail());

            Map<String, String> response = new HashMap<>();
            response.put("message", "Password reset successfully");
            response.put("logoutMessage", "All your sessions have been logged out for security. Please login again.");

            logger.info("Password reset successfully for user: {}", user.getEmail());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error resetting password for {}: {}", passwordRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to reset password: " + e.getMessage()));
        }
    }

    private void incrementTokenVersionAndBlacklist(User user) {
        long newTokenVersion = user.getTokenVersion() + 1;
        user.setTokenVersion(newTokenVersion);
        userRepository.save(user);

        for (String token : new HashSet<>(blacklistedTokens)) {
            try {
                if (jwtUtil.getEmailFromToken(token) != null &&
                        jwtUtil.getEmailFromToken(token).equals(user.getEmail()) &&
                        jwtUtil.getTokenVersionFromToken(token) < newTokenVersion) {
                    blacklistedTokens.add(token);
                }
            } catch (Exception e) {
                // Skip invalid tokens
            }
        }

        logger.info("Token version incremented to {} for user: {}", newTokenVersion, user.getEmail());
    }

    public Set<String> getBlacklistedTokens() {
        return blacklistedTokens;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user and profile image", description = "Xóa user + ảnh đại diện + vô hiệu tất cả token")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String id) {
        try {
            Optional<User> userOpt = userService.findById(id);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found with ID: " + id));
            }

            User user = userOpt.get();

            String profileImageUrl = user.getProfileImageUrl();
            if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                try {
                    String fileName = profileImageUrl.substring(profileImageUrl.lastIndexOf("/") + 1);
                    Path filePath = Paths.get(UPLOAD_DIR + fileName);

                    if (Files.exists(filePath)) {
                        Files.delete(filePath);
                        logger.info("Deleted profile image: {}", filePath);
                    } else {
                        logger.warn("Profile image not found on disk: {}", filePath);
                    }
                } catch (IOException e) {
                    logger.error("Failed to delete profile image for user {}: {}", user.getEmail(), e.getMessage());
                }
            }

            incrementTokenVersionAndBlacklist(user);
            logger.info("All tokens invalidated for user: {}", user.getEmail());

            userService.deleteUser(id);

            appSocketPublisher.userChanged("DELETED", id);

            return ResponseEntity.ok(Map.of(
                    "message", "User deleted successfully. Profile image removed. All sessions terminated."
            ));

        } catch (Exception e) {
            logger.error("Unexpected error deleting user ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete user: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/image")
    @Operation(summary = "Get user profile image", description = "Retrieve the profile image for a user")
    public ResponseEntity<?> getProfileImage(@PathVariable String id, @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or missing Authorization header"));
            }

            String token = authHeader.substring(7);

            if (blacklistedTokens.contains(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Session expired. Please login again."));
            }

            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid or expired token"));
            }

            String authEmail = jwtUtil.getEmailFromToken(token);

            Optional<User> userOpt = userService.findById(id);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "User not found with ID: " + id));
            }

            User user = userOpt.get();
            if (!authEmail.equals(user.getEmail()) && !"ADMIN".equals(jwtUtil.getRoleFromToken(token))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Unauthorized to access this user's image"));
            }

            String fileNamePattern = id + "_.*\\.(jpg|png|gif)";
            Path uploadPath = Paths.get(UPLOAD_DIR);
            Optional<Path> imagePath = Files.list(uploadPath)
                    .filter(path -> path.getFileName().toString().matches(fileNamePattern))
                    .findFirst();

            if (imagePath.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No profile image found for user with ID: " + id));
            }

            Path filePath = imagePath.get();
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : "image/jpeg"))
                    .body(resource);

        } catch (JwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid JWT token: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to retrieve profile image: " + e.getMessage()));
        }
    }

    private String normalizeApprovePermission(String value) {
        return normalizeNoticeDocumentPermission(value);
    }

    private String normalizeApprovePermissionFilter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String permission = value.trim().toUpperCase();

        if ("ALL".equals(permission)) {
            return "";
        }

        if (PERMISSION_NONE.equals(permission)
                || permission.contains(PERMISSION_NOTICE)
                || permission.contains(PERMISSION_DOCUMENT)
                || permission.contains(PERMISSION_BOTH_ALIAS)) {
            return normalizeNoticeDocumentPermission(permission);
        }

        return "";
    }

    private String normalizeModulePermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PERMISSION_NONE;
        }

        Set<String> permissions = new LinkedHashSet<>();

        for (String item : value.trim().toUpperCase().split(",")) {
            String cleanItem = item.trim();

            if ("ALL".equals(cleanItem)) {
                permissions.add(PERMISSION_NOTICE);
                permissions.add(PERMISSION_DOCUMENT);
                permissions.add(PERMISSION_APP_LINK);
            } else if (PERMISSION_BOTH_ALIAS.equals(cleanItem)) {
                permissions.add(PERMISSION_NOTICE);
                permissions.add(PERMISSION_DOCUMENT);
            } else if (PERMISSION_NOTICE.equals(cleanItem)
                    || PERMISSION_DOCUMENT.equals(cleanItem)
                    || PERMISSION_APP_LINK.equals(cleanItem)) {
                permissions.add(cleanItem);
            }
        }

        return permissions.isEmpty() ? PERMISSION_NONE : String.join(",", permissions);
    }

    private List<String> toModulePermissionList(String value) {
        String normalized = normalizeModulePermission(value);
        List<String> result = new ArrayList<>();

        if (PERMISSION_NONE.equals(normalized)) {
            result.add(PERMISSION_NONE);
            return result;
        }

        result.addAll(Arrays.asList(normalized.split(",")));
        return result;
    }

    private boolean hasModulePermission(String permissionValue, String target) {
        String normalized = normalizeModulePermission(permissionValue);
        return !PERMISSION_NONE.equals(normalized)
                && Arrays.asList(normalized.split(",")).contains(target);
    }

    private String normalizeNoticeDocumentPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PERMISSION_NONE;
        }

        String normalized = value.trim().toUpperCase();
        Set<String> permissions = new LinkedHashSet<>();

        for (String item : normalized.split(",")) {
            String cleanItem = item.trim();

            if (PERMISSION_BOTH_ALIAS.equals(cleanItem) || "ALL".equals(cleanItem)) {
                permissions.add(PERMISSION_NOTICE);
                permissions.add(PERMISSION_DOCUMENT);
            } else if (PERMISSION_NOTICE.equals(cleanItem) || PERMISSION_DOCUMENT.equals(cleanItem)) {
                permissions.add(cleanItem);
            }
        }

        if (permissions.isEmpty()) {
            return PERMISSION_NONE;
        }

        return String.join(",", permissions);
    }

    private List<String> toPermissionList(String value) {
        String normalized = normalizeNoticeDocumentPermission(value);
        List<String> result = new ArrayList<>();

        if (PERMISSION_NONE.equals(normalized)) {
            result.add(PERMISSION_NONE);
            return result;
        }

        result.addAll(Arrays.asList(normalized.split(",")));
        return result;
    }

    private boolean hasNoticeDocumentPermission(String permissionValue, String target) {
        String permission = normalizeNoticeDocumentPermission(permissionValue);

        if (PERMISSION_NONE.equals(permission)) {
            return false;
        }

        return Arrays.asList(permission.split(",")).contains(target);
    }

    private String normalizeBookingPermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BOOKING_NONE;
        }

        String permission = value.trim().toUpperCase();

        if (BOOKING_MANAGE.equals(permission) || BOOKING_NONE.equals(permission)) {
            return permission;
        }

        return BOOKING_NONE;
    }

    private String normalizeBookingPermissionFilter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        String permission = value.trim().toUpperCase();

        if ("ALL".equals(permission)) {
            return "";
        }

        if (BOOKING_MANAGE.equals(permission) || BOOKING_NONE.equals(permission)) {
            return permission;
        }

        return "";
    }

    private boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();

        return "Admin".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role)
                || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    private boolean isViewRole(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();
        return "VIEW".equalsIgnoreCase(role) || "ROLE_VIEW".equalsIgnoreCase(role);
    }

    private boolean canApproveNotice(User user) {
        if (isViewRole(user)) {
            return false;
        }

        return hasNoticeDocumentPermission(user != null ? user.getApprovePermission() : null, PERMISSION_NOTICE);
    }

    private boolean canApproveDocument(User user) {
        if (isViewRole(user)) {
            return false;
        }

        return hasNoticeDocumentPermission(user != null ? user.getApprovePermission() : null, PERMISSION_DOCUMENT);
    }

    private boolean canManageBooking(User user) {
        if (isViewRole(user)) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        String permission = normalizeBookingPermission(user != null ? user.getBookingPermission() : null);

        return BOOKING_MANAGE.equals(permission);
    }

    private boolean canManageAppLinks(User user) {
        if (isViewRole(user)) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        return hasModulePermission(user != null ? user.getModulePermission() : null, PERMISSION_APP_LINK);
    }

    private boolean canManageNotice(User user) {
        if (isViewRole(user)) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        return hasModulePermission(user != null ? user.getModulePermission() : null, PERMISSION_NOTICE);
    }

    private boolean canManageDocument(User user) {
        if (isViewRole(user)) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        return hasModulePermission(user != null ? user.getModulePermission() : null, PERMISSION_DOCUMENT);
    }

    private boolean canManageDepartment(User user) {
        return !isViewRole(user) && isAdmin(user);
    }

    private Map<String, Object> buildUserResponse(User user) {
        Map<String, Object> data = new LinkedHashMap<>();

        String departmentId = user.getDepartmentId();
        Map<String, Object> departmentMap = buildDepartmentResponse(departmentId);

        String departmentName = "";
        String division = "";

        if (departmentMap != null) {
            departmentName = safeString(departmentMap.get("departmentName"));
            division = safeString(departmentMap.get("division"));
        }

        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("address", user.getAddress());
        data.put("phone", user.getPhone());
        data.put("role", user.getRole());

        String approvePermission = normalizeApprovePermission(user.getApprovePermission());
        data.put("approvePermission", approvePermission);
        data.put("approvePermissions", toPermissionList(approvePermission));
        data.put("canApproveNotice", canApproveNotice(user));
        data.put("canApproveDocument", canApproveDocument(user));

        data.put("bookingPermission", normalizeBookingPermission(user.getBookingPermission()));
        data.put("canManageBooking", canManageBooking(user));

        String modulePermission = normalizeModulePermission(user.getModulePermission());
        data.put("modulePermission", modulePermission);
        data.put("modulePermissions", toModulePermissionList(modulePermission));
        data.put("canManageAppLinks", canManageAppLinks(user));
        data.put("canManageNotice", canManageNotice(user));
        data.put("canManageDocument", canManageDocument(user));
        data.put("canManageDepartment", canManageDepartment(user));

        data.put("profileImageUrl", user.getProfileImageUrl());
        data.put("createdAt", user.getCreatedAt());
        data.put("enabled", user.isEnabled());
        data.put("isEnabled", user.isEnabled());

        data.put("departmentId", departmentId);
        data.put("departmentName", departmentName);
        data.put("division", division);
        data.put("department", departmentMap);

        return data;
    }

    private Map<String, Object> buildDepartmentResponse(String departmentId) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return null;
        }

        String cleanDepartmentId = departmentId.trim();

        Map<String, Object> departmentMap = new LinkedHashMap<>();
        departmentMap.put("id", cleanDepartmentId);

        Object department = findDepartmentObject(cleanDepartmentId);

        if (department == null) {
            departmentMap.put("departmentName", "");
            departmentMap.put("name", "");
            departmentMap.put("division", "");
            return departmentMap;
        }

        String departmentName = firstText(
                invokeStringGetter(department, "getDepartmentName"),
                invokeStringGetter(department, "getName"),
                readStringField(department, "departmentName"),
                readStringField(department, "name")
        );

        String division = firstText(
                invokeStringGetter(department, "getDivision"),
                readStringField(department, "division")
        );

        departmentMap.put("departmentName", departmentName);
        departmentMap.put("name", departmentName);
        departmentMap.put("division", division);

        return departmentMap;
    }

    private Object findDepartmentObject(String departmentId) {
        if (departmentService == null || departmentId == null || departmentId.trim().isEmpty()) {
            return null;
        }

        String cleanDepartmentId = departmentId.trim();

        String[] methodNames = {
                "getById",
                "findById",
                "getDepartmentById",
                "findDepartmentById"
        };

        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = departmentService.getClass().getMethod(methodName, String.class);
                Object result = method.invoke(departmentService, cleanDepartmentId);

                if (result instanceof Optional<?>) {
                    return ((Optional<?>) result).orElse(null);
                }

                if (result instanceof ResponseEntity<?>) {
                    return ((ResponseEntity<?>) result).getBody();
                }

                if (result != null) {
                    return result;
                }
            } catch (Exception ignored) {
                // Try next method name
            }
        }

        return null;
    }

    private String invokeStringGetter(Object target, String methodName) {
        if (target == null || methodName == null || methodName.trim().isEmpty()) {
            return "";
        }

        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return safeString(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String readStringField(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.trim().isEmpty()) {
            return "";
        }

        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return safeString(value);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return "";
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
