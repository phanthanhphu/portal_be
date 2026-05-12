package org.bsl.portal.service;

import org.bsl.portal.dto.NoticeResponse;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.Notice;
import org.bsl.portal.repository.NoticeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
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
public class NoticeService {

    @Autowired
    private NoticeRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private DepartmentService departmentService;

    // CREATE
    public Notice create(Notice notice) {

        LocalDateTime now = LocalDateTime.now();

        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);

        Notice created = repository.save(notice);

        // Add notice id vào department.noticeIds sau khi MongoDB đã tạo id.
        departmentService.addNoticeToDepartment(
                created.getDepartmentId(),
                created.getId()
        );

        return created;
    }

    // UPDATE
    public Notice update(String id, Notice data) {

        Optional<Notice> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        Notice notice = optional.get();
        String oldDepartmentId = notice.getDepartmentId();

        notice.setTitle(data.getTitle());
        notice.setContent(data.getContent());
        notice.setFileUrl(data.getFileUrl());
        notice.setPreviewUrl(data.getPreviewUrl());
        notice.setFileUrls(cleanUrls(data.getFileUrls()));
        notice.setPreviewUrls(cleanUrls(data.getPreviewUrls()));

        // Keep old field synced with list fields
        if (notice.getFileUrls() != null && !notice.getFileUrls().isEmpty()) {
            notice.setFileUrl(notice.getFileUrls().get(0));
        } else {
            notice.setFileUrl(null);
        }

        if (notice.getPreviewUrls() != null && !notice.getPreviewUrls().isEmpty()) {
            notice.setPreviewUrl(notice.getPreviewUrls().get(0));
        } else {
            notice.setPreviewUrl(notice.getFileUrl());
        }

        notice.setPinned(data.getPinned());
        notice.setUserId(data.getUserId());
        notice.setDepartmentId(data.getDepartmentId());
        notice.setUpdatedAt(LocalDateTime.now());

        Notice updated = repository.save(notice);

        // Nếu đổi phòng ban: remove khỏi department cũ, add vào department mới.
        // Nếu không đổi phòng ban: vẫn đảm bảo id đã có trong department.noticeIds.
        departmentService.moveNoticeDepartment(
                oldDepartmentId,
                updated.getDepartmentId(),
                updated.getId()
        );

        return updated;
    }

    // DELETE
    public void delete(String id) {
        Notice existing = repository.findById(id).orElse(null);

        if (existing == null) {
            return;
        }

        String departmentId = existing.getDepartmentId();
        String noticeId = existing.getId();

        repository.deleteById(id);

        // Remove notice id khỏi department.noticeIds khi xóa notice.
        departmentService.removeNoticeFromDepartment(departmentId, noticeId);
    }

    // GET BY ID
    public Notice getById(String id) {
        return repository.findById(id).orElse(null);
    }

    // GET ALL PAGED
    public Page<Notice> getAllPaged(int page, int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "pinned", "createdAt")
        );

        return repository.findAll(pageable);
    }

    public Page<NoticeResponse> search(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            Pageable pageable
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content
        );

        if (query == null) {
            return Page.empty(pageable);
        }

        long total = mongoTemplate.count(query, Notice.class);

        List<Notice> notices = mongoTemplate.find(
                query.with(pageable),
                Notice.class
        );

        List<NoticeResponse> contentList = notices.stream()
                .map(this::toNoticeResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(contentList, pageable, total);
    }

    public NoticeResponse getLatestPinnedNotice(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content
        );

        if (query == null) {
            return null;
        }

        query.addCriteria(Criteria.where("pinned").is(true));
        query.with(
                Sort.by(Sort.Direction.DESC, "updatedAt")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        query.limit(1);

        Notice notice = mongoTemplate.findOne(query, Notice.class);

        return notice == null ? null : toNoticeResponse(notice);
    }

    public Page<NoticeResponse> searchExcludingNoticeId(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String excludeNoticeId,
            Pageable pageable
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content
        );

        if (query == null) {
            return Page.empty(pageable);
        }

        if (StringUtils.hasText(excludeNoticeId)) {
            query.addCriteria(Criteria.where("_id").ne(excludeNoticeId.trim()));
        }

        long total = mongoTemplate.count(query, Notice.class);

        List<Notice> notices = mongoTemplate.find(
                query.with(pageable),
                Notice.class
        );

        List<NoticeResponse> contentList = notices.stream()
                .map(this::toNoticeResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(contentList, pageable, total);
    }

    private Query buildSearchQuery(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content
    ) {
        List<String> effectiveDepartmentIds = null;

        if (StringUtils.hasText(division) || StringUtils.hasText(departmentName)) {
            List<Department> matchedDepartments = departmentService.search(
                    division,
                    departmentName,
                    0,
                    1000
            );

            effectiveDepartmentIds = matchedDepartments.stream()
                    .map(Department::getId)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList());

            if (effectiveDepartmentIds.isEmpty()) {
                return null;
            }
        }

        if (StringUtils.hasText(departmentId)) {
            String normalizedDepartmentId = departmentId.trim();
            Department department = departmentService.getById(normalizedDepartmentId);

            if (department == null) {
                return null;
            }

            if (effectiveDepartmentIds != null && !effectiveDepartmentIds.contains(normalizedDepartmentId)) {
                return null;
            }

            effectiveDepartmentIds = List.of(normalizedDepartmentId);
        }

        Query query = new Query();
        List<Criteria> andCriterias = new ArrayList<>();

        if (effectiveDepartmentIds != null && !effectiveDepartmentIds.isEmpty()) {
            andCriterias.add(Criteria.where("departmentId").in(effectiveDepartmentIds));
        }

        if (StringUtils.hasText(title)) {
            andCriterias.add(Criteria.where("title").regex(title.trim(), "i"));
        }

        if (StringUtils.hasText(content)) {
            andCriterias.add(Criteria.where("content").regex(content.trim(), "i"));
        }

        if (!andCriterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(
                    andCriterias.toArray(new Criteria[0])
            ));
        }

        return query;
    }

    private NoticeResponse toNoticeResponse(Notice notice) {
        NoticeResponse response = new NoticeResponse();

        response.setId(notice.getId());
        response.setTitle(notice.getTitle());
        response.setContent(notice.getContent());
        response.setFileUrl(notice.getFileUrl());
        response.setPreviewUrl(notice.getPreviewUrl());
        response.setFileUrls(cleanUrls(notice.getFileUrls()));
        response.setPreviewUrls(cleanUrls(notice.getPreviewUrls()));
        response.setPinned(notice.getPinned());
        response.setUserId(notice.getUserId());
        response.setDepartmentId(notice.getDepartmentId());
        response.setCreatedAt(notice.getCreatedAt());
        response.setUpdatedAt(notice.getUpdatedAt());

        if (StringUtils.hasText(notice.getDepartmentId())) {
            Department dept = departmentService.getById(notice.getDepartmentId());

            if (dept != null) {
                response.setDepartmentName(dept.getDepartmentName());
                response.setDivision(dept.getDivision());
            }
        }

        return response;
    }


    private List<String> cleanUrls(List<String> urls) {
        List<String> result = new ArrayList<>();

        if (urls == null) {
            return result;
        }

        for (String url : urls) {
            if (url != null && !url.trim().isEmpty() && !result.contains(url.trim())) {
                result.add(url.trim());
            }
        }

        return result;
    }

    // PIN / UNPIN
    public Notice pin(String id, Boolean pinned) {

        Notice notice = repository.findById(id).orElse(null);

        if (notice == null) {
            return null;
        }

        notice.setPinned(pinned);
        notice.setUpdatedAt(LocalDateTime.now());

        Notice updated = repository.save(notice);

        // Đảm bảo dữ liệu cũ cũng có noticeId trong department.noticeIds.
        departmentService.addNoticeToDepartment(updated.getDepartmentId(), updated.getId());

        return updated;
    }

    public boolean existsByDepartmentId(String departmentId) {
        return repository.existsByDepartmentId(departmentId);
    }
}
