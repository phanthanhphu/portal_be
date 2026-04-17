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

        return repository.save(notice);
    }

    // UPDATE
    public Notice update(String id, Notice data) {

        Optional<Notice> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        Notice notice = optional.get();

        notice.setTitle(data.getTitle());
        notice.setContent(data.getContent());
        notice.setFileUrl(data.getFileUrl());
        notice.setPinned(data.getPinned());
        notice.setUserId(data.getUserId());
        notice.setDepartmentId(data.getDepartmentId());

        notice.setUpdatedAt(LocalDateTime.now());

        return repository.save(notice);
    }

    // DELETE
    public void delete(String id) {
        repository.deleteById(id);
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
            String division,
            String departmentName,
            String title,
            String content,
            Pageable pageable
    ) {
        List<Department> matchedDepartments = departmentService.search(
                division,
                departmentName,
                0,
                1000
        );

        List<String> departmentIds = matchedDepartments.stream()
                .map(Department::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        Query query = new Query();
        List<Criteria> andCriterias = new ArrayList<>();

        if (StringUtils.hasText(title)) {
            andCriterias.add(Criteria.where("title").regex(title.trim(), "i"));
        }

        if (StringUtils.hasText(content)) {
            andCriterias.add(Criteria.where("content").regex(content.trim(), "i"));
        }

        if (StringUtils.hasText(division) || StringUtils.hasText(departmentName)) {
            if (departmentIds.isEmpty()) {
                return Page.empty(pageable);
            }
            andCriterias.add(Criteria.where("departmentId").in(departmentIds));
        }

        if (!andCriterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(
                    andCriterias.toArray(new Criteria[0])
            ));
        }

        long total = mongoTemplate.count(query, Notice.class);

        List<Notice> notices = mongoTemplate.find(
                query.with(pageable),
                Notice.class
        );

        List<NoticeResponse> contentList = notices.stream().map(notice -> {
            NoticeResponse response = new NoticeResponse();
            response.setId(notice.getId());
            response.setTitle(notice.getTitle());
            response.setContent(notice.getContent());
            response.setFileUrl(notice.getFileUrl());
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
        }).collect(Collectors.toList());

        return new PageImpl<>(contentList, pageable, total);
    }

    // PIN / UNPIN
    public Notice pin(String id, Boolean pinned) {

        Notice notice = repository.findById(id).orElse(null);

        if (notice == null) {
            return null;
        }

        notice.setPinned(pinned);
        notice.setUpdatedAt(LocalDateTime.now());

        return repository.save(notice);
    }
}
