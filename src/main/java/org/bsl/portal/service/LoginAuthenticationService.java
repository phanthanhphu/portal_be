package org.bsl.portal.service;

import org.bsl.portal.dto.LoginDTO;
import org.bsl.portal.enums.LoginType;
import org.bsl.portal.exception.DomainAuthenticationException;
import org.bsl.portal.exception.LoginFailureException;
import org.bsl.portal.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class LoginAuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final ActiveDirectoryService activeDirectoryService;

    public LoginAuthenticationService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            ActiveDirectoryService activeDirectoryService
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.activeDirectoryService = activeDirectoryService;
    }

    public LoginResult authenticate(LoginDTO request) {
        if (request == null) {
            throw failure(HttpStatus.BAD_REQUEST, "LOGIN_REQUEST_REQUIRED", "Login information is required.");
        }

        String identifier = request.resolveIdentifier();
        String password = request.getPassword();

        if (!StringUtils.hasText(identifier)) {
            throw failure(
                    HttpStatus.BAD_REQUEST,
                    "LOGIN_IDENTIFIER_REQUIRED",
                    "Username or email cannot be empty."
            );
        }

        if (!StringUtils.hasText(password)) {
            throw failure(HttpStatus.BAD_REQUEST, "LOGIN_PASSWORD_REQUIRED", "Password cannot be empty.");
        }

        final LoginType loginType;

        try {
            loginType = LoginType.from(request.getLoginType());
        } catch (IllegalArgumentException ex) {
            throw new LoginFailureException(
                    HttpStatus.BAD_REQUEST,
                    "LOGIN_TYPE_INVALID",
                    "Login type must be DOMAIN or SYSTEM.",
                    ex
            );
        }

        LoginResult result = loginType == LoginType.DOMAIN
                ? authenticateDomain(identifier, password)
                : authenticateSystem(identifier, password);

        validateApplicationAccess(result.getUser());
        return result;
    }

    private LoginResult authenticateDomain(String identifier, String password) {
        final ActiveDirectoryService.DirectoryUser directoryUser;

        try {
            directoryUser = activeDirectoryService.authenticate(identifier, password);
        } catch (DomainAuthenticationException ex) {
            throw mapDomainFailure(ex);
        }

        User user = userService.findByEmailIgnoreCase(directoryUser.getEmail())
                .orElseThrow(() -> failure(
                        HttpStatus.FORBIDDEN,
                        "DOMAIN_USER_NOT_AUTHORIZED",
                        "Domain authentication succeeded, but this email has not been granted access to the system. "
                                + "Please contact the administrator."
                ));

        return new LoginResult(user, LoginType.DOMAIN, directoryUser);
    }

    private LoginResult authenticateSystem(String identifier, String password) {
        Optional<User> userOptional = userService.findByEmailIgnoreCase(identifier);

        if (userOptional.isEmpty()) {
            userOptional = userService.findByUsernameIgnoreCase(identifier);
        }

        if (userOptional.isEmpty()) {
            throw invalidSystemCredentials();
        }

        User user = userOptional.get();
        String encodedPassword = user.getPassword();

        if (!StringUtils.hasText(encodedPassword) || !passwordEncoder.matches(password, encodedPassword)) {
            throw invalidSystemCredentials();
        }

        return new LoginResult(user, LoginType.SYSTEM, null);
    }

    private void validateApplicationAccess(User user) {
        if (user == null) {
            throw failure(HttpStatus.FORBIDDEN, "USER_NOT_AUTHORIZED", "This account is not authorized.");
        }

        if (!user.isEnabled()) {
            throw failure(
                    HttpStatus.FORBIDDEN,
                    "USER_DISABLED",
                    "Your system account has been disabled. Please contact the administrator."
            );
        }

        if (!StringUtils.hasText(user.getRole())) {
            throw failure(
                    HttpStatus.FORBIDDEN,
                    "USER_ROLE_NOT_ASSIGNED",
                    "Your account has not been assigned a system role. Please contact the administrator."
            );
        }
    }

    private LoginFailureException mapDomainFailure(DomainAuthenticationException ex) {
        switch (ex.getReason()) {
            case INVALID_CREDENTIALS:
                return new LoginFailureException(
                        HttpStatus.UNAUTHORIZED,
                        "DOMAIN_INVALID_CREDENTIALS",
                        "Domain username or password is incorrect.",
                        ex
                );
            case EMAIL_NOT_FOUND:
                return new LoginFailureException(
                        HttpStatus.FORBIDDEN,
                        "DOMAIN_EMAIL_NOT_FOUND",
                        ex.getMessage(),
                        ex
                );
            case USER_NOT_FOUND:
                return new LoginFailureException(
                        HttpStatus.FORBIDDEN,
                        "DOMAIN_PROFILE_NOT_FOUND",
                        ex.getMessage(),
                        ex
                );
            case DISABLED:
                return new LoginFailureException(
                        HttpStatus.FORBIDDEN,
                        "DOMAIN_ACCOUNT_DISABLED",
                        ex.getMessage(),
                        ex
                );
            case CONFIGURATION_ERROR:
                return new LoginFailureException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "DOMAIN_CONFIGURATION_ERROR",
                        ex.getMessage(),
                        ex
                );
            case SERVICE_UNAVAILABLE:
            default:
                return new LoginFailureException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "DOMAIN_SERVICE_UNAVAILABLE",
                        "The Domain authentication service is currently unavailable. Please try again or use System Account.",
                        ex
                );
        }
    }

    private LoginFailureException invalidSystemCredentials() {
        return failure(
                HttpStatus.UNAUTHORIZED,
                "SYSTEM_INVALID_CREDENTIALS",
                "System username/email or password is incorrect."
        );
    }

    private LoginFailureException failure(HttpStatus status, String code, String message) {
        return new LoginFailureException(status, code, message);
    }

    public static final class LoginResult {
        private final User user;
        private final LoginType loginType;
        private final ActiveDirectoryService.DirectoryUser directoryUser;

        public LoginResult(
                User user,
                LoginType loginType,
                ActiveDirectoryService.DirectoryUser directoryUser
        ) {
            this.user = user;
            this.loginType = loginType;
            this.directoryUser = directoryUser;
        }

        public User getUser() {
            return user;
        }

        public LoginType getLoginType() {
            return loginType;
        }

        public ActiveDirectoryService.DirectoryUser getDirectoryUser() {
            return directoryUser;
        }
    }
}
