package org.bsl.portal.service;

import org.bsl.portal.dto.UserDTO;
import org.bsl.portal.model.User;
import org.bsl.portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final String PERMISSION_NONE = "NONE";
    private static final String PERMISSION_NOTICE = "NOTICE";
    private static final String PERMISSION_DOCUMENT = "DOCUMENT";
    private static final String PERMISSION_APP_LINK = "APP_LINK";
    private static final String PERMISSION_BOTH_ALIAS = "BOTH";

    private static final String BOOKING_NONE = "NONE";
    private static final String BOOKING_MANAGE = "BOOKING";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    public User saveUser(User user) {
        if (user.getId() == null || user.getId().trim().isEmpty()) {
            user.setCreatedAt(LocalDateTime.now());
        }

        if (isViewRole(user)) {
            user.setApprovePermission(PERMISSION_NONE);
            user.setBookingPermission(BOOKING_NONE);
            user.setModulePermission(PERMISSION_NONE);
        } else {
            user.setApprovePermission(normalizeNoticeDocumentPermission(user.getApprovePermission()));
            user.setBookingPermission(normalizeBookingPermission(user.getBookingPermission()));
            user.setModulePermission(normalizeModulePermission(user.getModulePermission()));
        }

        return userRepository.save(user);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByEmailIgnoreCase(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }

        return userRepository.findByEmailIgnoreCase(email.trim());
    }

    public Optional<User> findByUsernameIgnoreCase(String username) {
        if (!StringUtils.hasText(username)) {
            return Optional.empty();
        }

        return userRepository.findByUsernameIgnoreCase(username.trim());
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public User updateUser(String id, User data) {
        Optional<User> optional = userRepository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        User existing = optional.get();

        existing.setUsername(data.getUsername());
        existing.setEmail(data.getEmail());
        existing.setAddress(data.getAddress());
        existing.setPhone(data.getPhone());
        existing.setRole(data.getRole());
        existing.setDepartmentId(data.getDepartmentId());
        existing.setEnabled(data.isEnabled());
        existing.setTokenVersion(data.getTokenVersion() > 0 ? data.getTokenVersion() : existing.getTokenVersion());
        existing.setProfileImageUrl(data.getProfileImageUrl());

        if (isViewRole(existing)) {
            existing.setApprovePermission(PERMISSION_NONE);
            existing.setBookingPermission(BOOKING_NONE);
            existing.setModulePermission(PERMISSION_NONE);
        } else {
            existing.setApprovePermission(normalizeNoticeDocumentPermission(data.getApprovePermission()));
            existing.setBookingPermission(normalizeBookingPermission(data.getBookingPermission()));
            existing.setModulePermission(normalizeModulePermission(data.getModulePermission()));
        }

        if (existing.getCreatedAt() == null) {
            existing.setCreatedAt(data.getCreatedAt() != null ? data.getCreatedAt() : LocalDateTime.now());
        }

        return userRepository.save(existing);
    }

    public Page<UserDTO> filterUsers(
            String username,
            String address,
            String phone,
            String email,
            String role,
            Pageable pageable
    ) {
        return filterUsers(username, address, phone, email, role, "", "", pageable);
    }

    public Page<UserDTO> filterUsers(
            String username,
            String address,
            String phone,
            String email,
            String role,
            String approvePermission,
            Pageable pageable
    ) {
        return filterUsers(username, address, phone, email, role, approvePermission, "", pageable);
    }

    public Page<UserDTO> filterUsers(
            String username,
            String address,
            String phone,
            String email,
            String role,
            String approvePermission,
            String bookingPermission,
            Pageable pageable
    ) {
        Query query = new Query();
        List<Criteria> andCriterias = new ArrayList<>();

        if (StringUtils.hasText(username)) {
            andCriterias.add(Criteria.where("username").regex(username.trim(), "i"));
        }

        if (StringUtils.hasText(address)) {
            andCriterias.add(Criteria.where("address").regex(address.trim(), "i"));
        }

        if (StringUtils.hasText(phone)) {
            andCriterias.add(Criteria.where("phone").regex(phone.trim(), "i"));
        }

        if (StringUtils.hasText(email)) {
            andCriterias.add(Criteria.where("email").regex(email.trim(), "i"));
        }

        if (StringUtils.hasText(role)) {
            andCriterias.add(Criteria.where("role").regex("^" + role.trim() + "$", "i"));
        }

        String normalizedPermission = normalizePermissionFilter(approvePermission);
        if (StringUtils.hasText(normalizedPermission)) {
            andCriterias.add(buildNoticeDocumentPermissionCriteria("approvePermission", normalizedPermission));
        }

        String normalizedBookingPermission = normalizeBookingPermissionFilter(bookingPermission);
        if (StringUtils.hasText(normalizedBookingPermission)) {
            if (BOOKING_NONE.equals(normalizedBookingPermission)) {
                andCriterias.add(new Criteria().orOperator(
                        Criteria.where("bookingPermission").is(BOOKING_NONE),
                        Criteria.where("bookingPermission").exists(false),
                        Criteria.where("bookingPermission").is(null),
                        Criteria.where("bookingPermission").is("")
                ));
            } else {
                andCriterias.add(Criteria.where("bookingPermission").is(normalizedBookingPermission));
            }
        }

        if (!andCriterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(andCriterias.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(query, User.class);
        List<User> users = mongoTemplate.find(query.with(pageable), User.class);

        List<UserDTO> content = users.stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, total);
    }

    private UserDTO toUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setAddress(user.getAddress());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setProfileImageUrl(user.getProfileImageUrl());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setEnabled(user.isEnabled());
        dto.setDepartmentId(user.getDepartmentId());

        String approvePermission = normalizeNoticeDocumentPermission(user.getApprovePermission());
        dto.setApprovePermission(approvePermission);
        dto.setApprovePermissions(toPermissionList(approvePermission));
        dto.setCanApproveNotice(canApproveNotice(user));
        dto.setCanApproveDocument(canApproveDocument(user));

        dto.setBookingPermission(normalizeBookingPermission(user.getBookingPermission()));
        dto.setCanManageBooking(canManageBooking(user));

        String modulePermission = normalizeModulePermission(user.getModulePermission());
        dto.setModulePermission(modulePermission);
        dto.setModulePermissions(toModulePermissionList(modulePermission));
        dto.setCanManageAppLinks(canManageAppLinkModule(user));
        dto.setCanManageNotice(canManageNoticeModule(user));
        dto.setCanManageDocument(canManageDocumentModule(user));
        dto.setCanManageDepartment(canManageDepartmentModule(user));

        return dto;
    }

    private Criteria buildNoticeDocumentPermissionCriteria(String field, String normalizedPermission) {
        if (PERMISSION_NONE.equals(normalizedPermission)) {
            return new Criteria().orOperator(
                    Criteria.where(field).is(PERMISSION_NONE),
                    Criteria.where(field).exists(false),
                    Criteria.where(field).is(null),
                    Criteria.where(field).is("")
            );
        }

        if (PERMISSION_NOTICE.equals(normalizedPermission) || PERMISSION_DOCUMENT.equals(normalizedPermission)) {
            return new Criteria().orOperator(
                    Criteria.where(field).is(normalizedPermission),
                    Criteria.where(field).regex("(^|,)" + normalizedPermission + "(,|$)", "i"),
                    Criteria.where(field).is(PERMISSION_BOTH_ALIAS),
                    Criteria.where(field).is("ALL")
            );
        }

        return new Criteria().orOperator(
                Criteria.where(field).is("NOTICE,DOCUMENT"),
                Criteria.where(field).is("DOCUMENT,NOTICE"),
                Criteria.where(field).is(PERMISSION_BOTH_ALIAS),
                Criteria.where(field).is("ALL")
        );
    }

    private String normalizeNoticeDocumentPermission(String value) {
        if (!StringUtils.hasText(value)) {
            return PERMISSION_NONE;
        }

        String permission = value.trim().toUpperCase();
        Set<String> permissions = new LinkedHashSet<>();

        for (String item : permission.split(",")) {
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

    private String normalizeModulePermission(String value) {
        if (!StringUtils.hasText(value)) {
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

    private String normalizePermissionFilter(String value) {
        if (!StringUtils.hasText(value)) {
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

    private String normalizeBookingPermission(String value) {
        if (!StringUtils.hasText(value)) {
            return BOOKING_NONE;
        }

        String permission = value.trim().toUpperCase();

        if (BOOKING_MANAGE.equals(permission) || BOOKING_NONE.equals(permission)) {
            return permission;
        }

        return BOOKING_NONE;
    }

    private String normalizeBookingPermissionFilter(String value) {
        if (!StringUtils.hasText(value)) {
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

    public boolean isAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();

        return "Admin".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role)
                || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    public boolean isViewRole(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();
        return "VIEW".equalsIgnoreCase(role) || "ROLE_VIEW".equalsIgnoreCase(role);
    }

    private boolean hasNoticeDocumentPermission(String permissionValue, String target) {
        String permission = normalizeNoticeDocumentPermission(permissionValue);

        if (PERMISSION_NONE.equals(permission)) {
            return false;
        }

        return Arrays.asList(permission.split(",")).contains(target);
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

    public boolean canManageBooking(User user) {
        if (isViewRole(user)) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        String permission = normalizeBookingPermission(user != null ? user.getBookingPermission() : null);
        return BOOKING_MANAGE.equals(permission);
    }

    private boolean hasModulePermission(User user, String target) {
        if (isViewRole(user)) {
            return false;
        }

        if (isAdmin(user)) {
            return true;
        }

        String permission = normalizeModulePermission(user != null ? user.getModulePermission() : null);
        return !PERMISSION_NONE.equals(permission)
                && Arrays.asList(permission.split(",")).contains(target);
    }

    private boolean canManageAppLinkModule(User user) {
        return hasModulePermission(user, PERMISSION_APP_LINK);
    }

    private boolean canManageNoticeModule(User user) {
        return hasModulePermission(user, PERMISSION_NOTICE);
    }

    private boolean canManageDocumentModule(User user) {
        return hasModulePermission(user, PERMISSION_DOCUMENT);
    }

    private boolean canManageDepartmentModule(User user) {
        return isAdmin(user);
    }
}
