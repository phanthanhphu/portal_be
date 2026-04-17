package org.bsl.portal.service;

import org.bsl.portal.dto.UserDTO;
import org.bsl.portal.model.User;
import org.bsl.portal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MongoTemplate mongoTemplate;

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    public User saveUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public User updateUser(String id, User user) {
        Optional<User> existingUserOpt = userRepository.findById(id);
        if (!existingUserOpt.isPresent()) {
            throw new RuntimeException("User not found with ID: " + id);
        }
        User existingUser = existingUserOpt.get();
        existingUser.setUsername(user.getUsername());
        existingUser.setEmail(user.getEmail());
        existingUser.setAddress(user.getAddress());
        existingUser.setPhone(user.getPhone());
        existingUser.setRole(user.getRole());
        existingUser.setProfileImageUrl(user.getProfileImageUrl());
        existingUser.setCreatedAt(user.getCreatedAt());
        existingUser.setTokenVersion(user.getTokenVersion());
        existingUser.setEnabled(user.isEnabled());
        existingUser.setDepartmentId(user.getDepartmentId());
        return userRepository.save(existingUser);
    }

    public boolean changePassword(String email, String oldPassword, String newPassword) {
        if (email == null || email.trim().isEmpty()) {
            System.out.println("Email is null or empty");
            return false;
        }
        System.out.println("ChangePassword called with email: " + email + ", oldPassword: " + oldPassword);
        Optional<User> userOpt = userRepository.findByEmail(email.toLowerCase());
        if (!userOpt.isPresent()) {
            System.out.println("User not found for email: " + email);
            return false;
        }
        User user = userOpt.get();
        System.out.println("Stored hashed password: " + user.getPassword());
        boolean passwordMatch = passwordEncoder.matches(oldPassword, user.getPassword());
        System.out.println("Password match result: " + passwordMatch);
        if (passwordMatch) {
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            System.out.println("Password updated for user: " + email);
            return true;
        }
        return false;
    }

    public void deleteUser(String id) {
        userRepository.deleteById(id);
    }

    public Page<UserDTO> filterUsers(String username, String address, String phone, String email, String role, Pageable pageable) {
        Query query = new Query().with(pageable);

        // Add filtering criteria if provided
        if (username != null && !username.isEmpty()) {
            query.addCriteria(Criteria.where("username").regex(username, "i"));
        }
        if (address != null && !address.isEmpty()) {
            query.addCriteria(Criteria.where("address").regex(address, "i"));
        }
        if (phone != null && !phone.isEmpty()) {
            query.addCriteria(Criteria.where("phone").regex(phone, "i"));
        }
        if (email != null && !email.isEmpty()) {
            query.addCriteria(Criteria.where("email").regex(email, "i"));
        }
        if (role != null && !role.isEmpty()) {
            query.addCriteria(Criteria.where("role").is(role));
        }

        List<User> users = mongoTemplate.find(query, User.class);
        List<UserDTO> userDTOs = users.stream().map(user -> {
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
            return dto;
        }).collect(Collectors.toList());

        long total = mongoTemplate.count(query.skip(0).limit(0), User.class);
        return PageableExecutionUtils.getPage(userDTOs, pageable, () -> total);
    }
}