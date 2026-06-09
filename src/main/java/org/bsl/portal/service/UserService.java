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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final String APPROVE_NONE = "NONE";
    private static final String APPROVE_NOTICE = "NOTICE";
    private static final String APPROVE_DOCUMENT = "DOCUMENT";
    private static final String APPROVE_BOTH = "BOTH";

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

        user.setApprovePermission(normalizeApprovePermission(user.getApprovePermission()));
        user.setBookingPermission(normalizeBookingPermission(user.getBookingPermission()));
        return userRepository.save(user);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
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

        existing.setApprovePermission(normalizeApprovePermission(data.getApprovePermission()));
        existing.setBookingPermission(normalizeBookingPermission(data.getBookingPermission()));

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

        String normalizedPermission = normalizeApprovePermissionFilter(approvePermission);
        if (StringUtils.hasText(normalizedPermission)) {
            if (APPROVE_NONE.equals(normalizedPermission)) {
                andCriterias.add(new Criteria().orOperator(
                        Criteria.where("approvePermission").is(APPROVE_NONE),
                        Criteria.where("approvePermission").exists(false),
                        Criteria.where("approvePermission").is(null),
                        Criteria.where("approvePermission").is("")
                ));
            } else {
                andCriterias.add(Criteria.where("approvePermission").is(normalizedPermission));
            }
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
        dto.setApprovePermission(normalizeApprovePermission(user.getApprovePermission()));
        dto.setCanApproveNotice(canApproveNotice(user));
        dto.setCanApproveDocument(canApproveDocument(user));

        dto.setBookingPermission(normalizeBookingPermission(user.getBookingPermission()));
        dto.setCanManageBooking(canManageBooking(user));

        return dto;
    }

    private String normalizeApprovePermission(String value) {
        if (!StringUtils.hasText(value)) {
            return APPROVE_NONE;
        }

        String permission = value.trim().toUpperCase();

        if (APPROVE_NOTICE.equals(permission)
                || APPROVE_DOCUMENT.equals(permission)
                || APPROVE_BOTH.equals(permission)
                || APPROVE_NONE.equals(permission)) {
            return permission;
        }

        return APPROVE_NONE;
    }

    private String normalizeApprovePermissionFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String permission = value.trim().toUpperCase();

        if ("ALL".equals(permission)) {
            return "";
        }

        if (APPROVE_NOTICE.equals(permission)
                || APPROVE_DOCUMENT.equals(permission)
                || APPROVE_BOTH.equals(permission)
                || APPROVE_NONE.equals(permission)) {
            return permission;
        }

        return "";
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

    private boolean canApproveNotice(User user) {
        if (isAdmin(user)) {
            return true;
        }

        String permission = normalizeApprovePermission(user != null ? user.getApprovePermission() : null);
        return APPROVE_NOTICE.equals(permission) || APPROVE_BOTH.equals(permission);
    }

    private boolean canApproveDocument(User user) {
        if (isAdmin(user)) {
            return true;
        }

        String permission = normalizeApprovePermission(user != null ? user.getApprovePermission() : null);
        return APPROVE_DOCUMENT.equals(permission) || APPROVE_BOTH.equals(permission);
    }

    public boolean canManageBooking(User user) {
        if (isAdmin(user)) {
            return true;
        }

        String permission = normalizeBookingPermission(user != null ? user.getBookingPermission() : null);
        return BOOKING_MANAGE.equals(permission);
    }
}
