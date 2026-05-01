package org.bsl.portal.service;

import org.bsl.portal.model.Department;
import org.bsl.portal.model.Notice;
import org.bsl.portal.repository.DepartmentRepository;
import org.bsl.portal.repository.NoticeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    @Autowired
    private DepartmentRepository repository;

    @Autowired
    private NoticeRepository noticeRepository;

    public Department create(String division, String departmentName) {
        division = division != null ? division.trim() : "";
        departmentName = departmentName != null ? departmentName.trim() : "";

        if (division.isEmpty()) throw new RuntimeException("Division is required");
        if (departmentName.isEmpty()) throw new RuntimeException("Department name is required");

        boolean exists = repository.existsByDivisionAndDepartmentName(division, departmentName);
        if (exists) throw new RuntimeException("Department already exists in this division");

        Department department = new Department();
        department.setDivision(division);
        department.setDepartmentName(departmentName);
        department.setNoticeIds(new ArrayList<>());

        LocalDateTime now = LocalDateTime.now();
        department.setCreatedAt(now);
        department.setUpdatedAt(now);

        return repository.save(department);
    }

    public List<Department> getAll() {
        return repository.findAll();
    }

    public List<Department> getAll(String division, String departmentName) {
        division = division != null ? division.trim() : "";
        departmentName = departmentName != null ? departmentName.trim() : "";

        if (division.isEmpty() && departmentName.isEmpty()) {
            return repository.findAll();
        } else if (!division.isEmpty() && departmentName.isEmpty()) {
            return repository.findByDivisionContainingIgnoreCase(division);
        } else if (division.isEmpty()) {
            return repository.findByDepartmentNameContainingIgnoreCase(departmentName);
        } else {
            return repository.findByDivisionContainingIgnoreCaseAndDepartmentNameContainingIgnoreCase(division, departmentName);
        }
    }

    public List<Department> search(String division, String departmentName, int page, int size) {
        List<Department> result = getAll(division, departmentName);
        if (page < 0) page = 0;
        if (size <= 0) size = result.size() > 0 ? result.size() : 1;
        int fromIndex = page * size;
        if (fromIndex >= result.size()) return List.of();
        int toIndex = Math.min(fromIndex + size, result.size());
        return result.subList(fromIndex, toIndex);
    }

    public Department getById(String id) {
        if (!StringUtils.hasText(id)) return null;
        return repository.findById(id.trim()).orElse(null);
    }

    public Department update(String id, String division, String departmentName) {
        division = division != null ? division.trim() : "";
        departmentName = departmentName != null ? departmentName.trim() : "";

        if (division.isEmpty()) throw new RuntimeException("Division is required");
        if (departmentName.isEmpty()) throw new RuntimeException("Department name is required");

        Optional<Department> optional = repository.findById(id);
        if (optional.isEmpty()) return null;

        Optional<Department> duplicate = repository.findByDivisionAndDepartmentName(division, departmentName);
        if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
            throw new RuntimeException("Department already exists in this division");
        }

        Department existing = optional.get();
        existing.setDivision(division);
        existing.setDepartmentName(departmentName);
        if (existing.getNoticeIds() == null) existing.setNoticeIds(new ArrayList<>());
        existing.setUpdatedAt(LocalDateTime.now());

        return repository.save(existing);
    }

    public void delete(String id) {
        if (!repository.existsById(id)) throw new RuntimeException("Department not found");
        repository.deleteById(id);
    }

    public Department findByDepartmentName(String departmentName) {
        return repository.findByDepartmentNameIgnoreCase(departmentName).orElse(null);
    }

    // ==================== NOTICE IDS MAPPING ====================

    public Department addNoticeToDepartment(String departmentId, String noticeId) {
        if (!StringUtils.hasText(departmentId) || !StringUtils.hasText(noticeId)) return null;

        Optional<Department> optional = repository.findById(departmentId.trim());
        if (optional.isEmpty()) return null;

        Department department = optional.get();
        List<String> noticeIds = normalizeNoticeIds(department.getNoticeIds());

        if (!noticeIds.contains(noticeId.trim())) {
            noticeIds.add(noticeId.trim());
            department.setNoticeIds(noticeIds);
            department.setUpdatedAt(LocalDateTime.now());
            return repository.save(department);
        }

        department.setNoticeIds(noticeIds);
        return department;
    }

    public Department removeNoticeFromDepartment(String departmentId, String noticeId) {
        if (!StringUtils.hasText(departmentId) || !StringUtils.hasText(noticeId)) return null;

        Optional<Department> optional = repository.findById(departmentId.trim());
        if (optional.isEmpty()) return null;

        Department department = optional.get();
        List<String> noticeIds = normalizeNoticeIds(department.getNoticeIds());
        boolean removed = noticeIds.removeIf(id -> noticeId.trim().equals(id));

        if (removed) {
            department.setNoticeIds(noticeIds);
            department.setUpdatedAt(LocalDateTime.now());
            return repository.save(department);
        }

        department.setNoticeIds(noticeIds);
        return department;
    }

    public void moveNoticeDepartment(String oldDepartmentId, String newDepartmentId, String noticeId) {
        if (!StringUtils.hasText(noticeId)) return;

        String oldId = StringUtils.hasText(oldDepartmentId) ? oldDepartmentId.trim() : null;
        String newId = StringUtils.hasText(newDepartmentId) ? newDepartmentId.trim() : null;

        if (oldId != null && newId != null && oldId.equals(newId)) {
            addNoticeToDepartment(newId, noticeId);
            return;
        }

        if (oldId != null) removeNoticeFromDepartment(oldId, noticeId);
        if (newId != null) addNoticeToDepartment(newId, noticeId);
    }

    public Department syncNoticeIdsForDepartment(String departmentId) {
        if (!StringUtils.hasText(departmentId)) return null;

        Optional<Department> optional = repository.findById(departmentId.trim());
        if (optional.isEmpty()) return null;

        Department department = optional.get();
        List<String> noticeIds = noticeRepository.findByDepartmentId(department.getId())
                .stream()
                .map(Notice::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        department.setNoticeIds(noticeIds);
        department.setUpdatedAt(LocalDateTime.now());
        return repository.save(department);
    }

    public List<Department> syncNoticeIdsForAllDepartments() {
        return repository.findAll()
                .stream()
                .map(department -> syncNoticeIdsForDepartment(department.getId()))
                .filter(department -> department != null)
                .collect(Collectors.toList());
    }

    private List<String> normalizeNoticeIds(List<String> noticeIds) {
        if (noticeIds == null || noticeIds.isEmpty()) return new ArrayList<>();
        Set<String> uniqueIds = new LinkedHashSet<>();
        for (String id : noticeIds) {
            if (StringUtils.hasText(id)) uniqueIds.add(id.trim());
        }
        return new ArrayList<>(uniqueIds);
    }
}
