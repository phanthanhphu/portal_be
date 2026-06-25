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
import java.util.regex.Pattern;

@Service
public class NoticeService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_ALL = "ALL";
    private static final int MAX_PINNED_NOTICES = 2;

    @Autowired
    private NoticeRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private DepartmentService departmentService;

    public static class PinNoticeResult {
        private final Notice notice;
        private final List<String> autoUnpinnedNoticeIds;

        public PinNoticeResult(Notice notice, List<String> autoUnpinnedNoticeIds) {
            this.notice = notice;
            this.autoUnpinnedNoticeIds = autoUnpinnedNoticeIds != null
                    ? autoUnpinnedNoticeIds
                    : new ArrayList<>();
        }

        public Notice getNotice() {
            return notice;
        }

        public List<String> getAutoUnpinnedNoticeIds() {
            return autoUnpinnedNoticeIds;
        }
    }

    // CREATE
    public Notice create(Notice notice) {

        LocalDateTime now = LocalDateTime.now();

        notice.setCreatedAt(now);
        notice.setUpdatedAt(now);

        // Nếu controller chưa set status thì mặc định APPROVED để không làm mất dữ liệu cũ.
        // Controller mới sẽ set user thường = PENDING, admin = APPROVED.
        if (!StringUtils.hasText(notice.getStatus())) {
            notice.setStatus(STATUS_APPROVED);
        } else {
            notice.setStatus(normalizeApprovalStatus(notice.getStatus()));
        }

        Notice created = repository.save(notice);

        // Add notice id vào department.noticeIds sau khi MongoDB đã tạo id.
        departmentService.addNoticeToDepartment(
                created.getDepartmentId(),
                created.getId()
        );

        return created;
    }

    // SAVE DIRECTLY
    // Dùng cho approve/reject trong NoticeController.
    public Notice save(Notice notice) {
        if (notice == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        if (notice.getCreatedAt() == null) {
            notice.setCreatedAt(now);
        }

        notice.setUpdatedAt(now);

        if (!StringUtils.hasText(notice.getStatus())) {
            notice.setStatus(STATUS_APPROVED);
        } else {
            notice.setStatus(normalizeApprovalStatus(notice.getStatus()));
        }

        Notice saved = repository.save(notice);

        // Đảm bảo dữ liệu cũ hoặc notice vừa save vẫn được gắn vào department.
        departmentService.addNoticeToDepartment(saved.getDepartmentId(), saved.getId());

        return saved;
    }

    /**
     * Save dùng cho approve/change status.
     * Chỉ enforce tối đa 2 pinned khi notice sau khi save là APPROVED + pinned=true.
     * Nếu pinned=false hoặc status chưa APPROVED thì không query danh sách pinned.
     */
    public PinNoticeResult saveWithAutoUnpinIfApprovedPinned(Notice notice) {
        Notice saved = save(notice);

        if (saved == null) {
            return null;
        }

        List<String> autoUnpinnedNoticeIds = enforceMaxPinnedIfApprovedAndPinned(saved);

        return new PinNoticeResult(saved, autoUnpinnedNoticeIds);
    }

    /**
     * Update dùng cho màn edit.
     * FE gửi pinned=false: chỉ update notice, KHÔNG query pinned.
     * FE gửi pinned=true nhưng notice chưa APPROVED: chỉ update notice, KHÔNG query pinned.
     * FE gửi pinned=true và notice APPROVED: query tối đa 2 pinned APPROVED hiện có,
     * bỏ ghim cái cũ hơn, giữ 1 pinned cũ mới nhất + notice hiện tại.
     */
    public PinNoticeResult updateWithAutoUnpinIfApprovedPinned(String id, Notice data) {
        Notice updated = update(id, data);

        if (updated == null) {
            return null;
        }

        List<String> autoUnpinnedNoticeIds = enforceMaxPinnedIfApprovedAndPinned(updated);

        return new PinNoticeResult(updated, autoUnpinnedNoticeIds);
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

        // Controller đã quyết định status sau update. Ví dụ user thường sửa bài thì quay lại PENDING.
        if (StringUtils.hasText(data.getStatus())) {
            notice.setStatus(normalizeApprovalStatus(data.getStatus()));
        } else if (!StringUtils.hasText(notice.getStatus())) {
            notice.setStatus(STATUS_APPROVED);
        } else {
            notice.setStatus(normalizeApprovalStatus(notice.getStatus()));
        }

        notice.setApprovedBy(data.getApprovedBy());
        notice.setApprovedAt(data.getApprovedAt());
        notice.setRejectedBy(data.getRejectedBy());
        notice.setRejectedAt(data.getRejectedAt());
        notice.setRejectReason(data.getRejectReason());

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

    // Search cũ: giữ lại để không làm lỗi code đang gọi hiện tại.
    public Page<NoticeResponse> search(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            Pageable pageable
    ) {
        return search(
                departmentId,
                division,
                departmentName,
                title,
                content,
                STATUS_ALL,
                pageable
        );
    }

    // Search mới: hỗ trợ lọc trạng thái duyệt bài.
    public Page<NoticeResponse> search(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String status,
            Pageable pageable
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                status
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


    // Search optimized: trả về Notice entity để Controller không cần gọi getById từng dòng.
    public Page<Notice> searchNoticeEntities(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String status,
            Pageable pageable
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                status
        );

        return executeNoticeEntityPage(query, pageable);
    }

    public Page<Notice> searchPinnedNoticeEntities(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String status,
            Pageable pageable
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                status
        );

        if (query != null) {
            query.addCriteria(Criteria.where("pinned").is(true));
        }

        return executeNoticeEntityPage(query, pageable);
    }

    public Page<Notice> searchNoticeEntitiesExcludingIds(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String status,
            List<String> excludeNoticeIds,
            Pageable pageable
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                status
        );

        if (query != null && excludeNoticeIds != null && !excludeNoticeIds.isEmpty()) {
            List<String> cleanExcludeIds = excludeNoticeIds.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());

            if (!cleanExcludeIds.isEmpty()) {
                query.addCriteria(Criteria.where("_id").nin(cleanExcludeIds));
            }
        }

        return executeNoticeEntityPage(query, pageable);
    }

    private Page<Notice> executeNoticeEntityPage(Query query, Pageable pageable) {
        if (query == null) {
            return Page.empty(pageable);
        }

        long total = mongoTemplate.count(query, Notice.class);

        List<Notice> notices = mongoTemplate.find(
                query.with(pageable),
                Notice.class
        );

        return new PageImpl<>(notices, pageable, total);
    }

    public long countNoticeEntities(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String status
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                status
        );

        if (query == null) {
            return 0L;
        }

        return mongoTemplate.count(query, Notice.class);
    }

    // Latest pinned cũ: mặc định chỉ lấy APPROVED để bài pending không hiện ngoài index.
    public NoticeResponse getLatestPinnedNotice(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content
    ) {
        return getLatestPinnedNotice(
                departmentId,
                division,
                departmentName,
                title,
                content,
                STATUS_APPROVED
        );
    }

    public NoticeResponse getLatestPinnedNotice(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String status
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                status
        );

        if (query == null) {
            return null;
        }

        query.addCriteria(Criteria.where("pinned").is(true));
        query.with(
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        query.limit(1);

        Notice notice = mongoTemplate.findOne(query, Notice.class);

        return notice == null ? null : toNoticeResponse(notice);
    }

    // Search excluding cũ: giữ lại để không làm lỗi code đang gọi hiện tại.
    public Page<NoticeResponse> searchExcludingNoticeId(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String excludeNoticeId,
            Pageable pageable
    ) {
        return searchExcludingNoticeId(
                departmentId,
                division,
                departmentName,
                title,
                content,
                excludeNoticeId,
                STATUS_ALL,
                pageable
        );
    }

    public Page<NoticeResponse> searchExcludingNoticeId(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String excludeNoticeId,
            String status,
            Pageable pageable
    ) {
        Query query = buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                status
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
        return buildSearchQuery(
                departmentId,
                division,
                departmentName,
                title,
                content,
                STATUS_ALL
        );
    }

    private Query buildSearchQuery(
            String departmentId,
            String division,
            String departmentName,
            String title,
            String content,
            String status
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

        String cleanTitle = StringUtils.hasText(title) ? title.trim() : null;
        String cleanContent = StringUtils.hasText(content) ? content.trim() : null;

        if (StringUtils.hasText(cleanTitle)
                && StringUtils.hasText(cleanContent)
                && cleanTitle.equalsIgnoreCase(cleanContent)) {
            // FE approval page uses one box named "Search title/content" and sends
            // the same keyword to both title and content. Treat that as OR search.
            String keywordRegex = Pattern.quote(cleanTitle);
            andCriterias.add(new Criteria().orOperator(
                    Criteria.where("title").regex(keywordRegex, "i"),
                    Criteria.where("content").regex(keywordRegex, "i")
            ));
        } else {
            if (StringUtils.hasText(cleanTitle)) {
                andCriterias.add(Criteria.where("title").regex(Pattern.quote(cleanTitle), "i"));
            }

            if (StringUtils.hasText(cleanContent)) {
                andCriterias.add(Criteria.where("content").regex(Pattern.quote(cleanContent), "i"));
            }
        }

        Criteria statusCriteria = buildStatusCriteria(status);
        if (statusCriteria != null) {
            andCriterias.add(statusCriteria);
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

    private Criteria buildStatusCriteria(String status) {
        String normalizedStatus = normalizeApprovalStatusFilter(status);

        if (STATUS_ALL.equals(normalizedStatus)) {
            return null;
        }

        if (STATUS_APPROVED.equals(normalizedStatus)) {
            // Dữ liệu cũ chưa có field status vẫn được xem là APPROVED.
            return new Criteria().orOperator(
                    Criteria.where("status").is(STATUS_APPROVED),
                    Criteria.where("status").exists(false),
                    Criteria.where("status").is(null),
                    Criteria.where("status").is("")
            );
        }

        return Criteria.where("status").is(normalizedStatus);
    }

    private String normalizeApprovalStatus(Object value) {
        if (value == null) {
            return STATUS_APPROVED;
        }

        String status = String.valueOf(value).trim().toUpperCase();

        if (STATUS_PENDING.equals(status)
                || STATUS_APPROVED.equals(status)
                || STATUS_REJECTED.equals(status)) {
            return status;
        }

        return STATUS_APPROVED;
    }

    private String normalizeApprovalStatusFilter(String value) {
        if (!StringUtils.hasText(value)) {
            return STATUS_APPROVED;
        }

        String status = value.trim().toUpperCase();

        if (STATUS_ALL.equals(status)
                || STATUS_PENDING.equals(status)
                || STATUS_APPROVED.equals(status)
                || STATUS_REJECTED.equals(status)) {
            return status;
        }

        return STATUS_APPROVED;
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
        PinNoticeResult result = pinWithAutoUnpin(id, pinned);

        return result != null ? result.getNotice() : null;
    }

    public PinNoticeResult pinWithAutoUnpin(String id, Boolean pinned) {

        Notice notice = repository.findById(id).orElse(null);

        if (notice == null) {
            return null;
        }

        boolean pinRequested = Boolean.TRUE.equals(pinned);

        /*
         * Rule theo request FE:
         * - pinned=false: chỉ update chính notice này, KHÔNG query danh sách pinned.
         * - pinned=true nhưng notice chưa APPROVED: chỉ update chính notice này, KHÔNG query danh sách pinned.
         * - pinned=true và notice APPROVED: mới query pinned APPROVED tối đa 2 dòng
         *   để bỏ ghim cái cũ hơn, giữ 1 pinned cũ mới nhất + notice hiện tại.
         */
        notice.setPinned(pinRequested);
        notice.setUpdatedAt(LocalDateTime.now());

        if (!StringUtils.hasText(notice.getStatus())) {
            notice.setStatus(STATUS_APPROVED);
        } else {
            notice.setStatus(normalizeApprovalStatus(notice.getStatus()));
        }

        Notice updated = repository.save(notice);

        // Đảm bảo dữ liệu cũ cũng có noticeId trong department.noticeIds.
        departmentService.addNoticeToDepartment(updated.getDepartmentId(), updated.getId());

        List<String> autoUnpinnedNoticeIds = enforceMaxPinnedIfApprovedAndPinned(updated);

        return new PinNoticeResult(updated, autoUnpinnedNoticeIds);
    }

    private List<String> enforceMaxPinnedIfApprovedAndPinned(Notice activeNotice) {
        /*
         * Chốt logic tốc độ:
         * - Chỉ khi activeNotice pinned=true và status=APPROVED mới query danh sách pinned.
         * - Nếu pinned=false / PENDING / REJECTED thì return ngay, không query MongoDB.
         */
        if (activeNotice == null
                || !Boolean.TRUE.equals(activeNotice.getPinned())
                || !STATUS_APPROVED.equals(normalizeApprovalStatus(activeNotice.getStatus()))) {
            return new ArrayList<>();
        }

        return unpinOldestApprovedPinnedNoticesBeforeKeeping(activeNotice.getId());
    }

    private List<String> unpinOldestApprovedPinnedNoticesBeforeKeeping(String activePinnedNoticeId) {
        /*
         * Chỉ lấy tối đa 2 notice đang pinned=true + APPROVED, loại trừ notice hiện tại.
         * Nếu đã có 2 notice pinned APPROVED khác thì bỏ cái cũ hơn trong 2 cái đó,
         * giữ lại 1 cái mới nhất + notice hiện tại = tổng cộng 2 pinned mới nhất.
         */
        Query query = new Query();

        Criteria pinnedCriteria = Criteria.where("pinned").is(true);
        Criteria statusCriteria = buildStatusCriteria(STATUS_APPROVED);

        if (statusCriteria != null) {
            query.addCriteria(new Criteria().andOperator(pinnedCriteria, statusCriteria));
        } else {
            query.addCriteria(pinnedCriteria);
        }

        if (StringUtils.hasText(activePinnedNoticeId)) {
            query.addCriteria(Criteria.where("_id").ne(activePinnedNoticeId.trim()));
        }

        query.with(
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        query.limit(MAX_PINNED_NOTICES);

        List<Notice> currentlyPinned = mongoTemplate.find(query, Notice.class);

        if (currentlyPinned.size() < MAX_PINNED_NOTICES) {
            return new ArrayList<>();
        }

        Notice oldPinnedNotice = currentlyPinned.get(MAX_PINNED_NOTICES - 1);
        oldPinnedNotice.setPinned(false);
        oldPinnedNotice.setUpdatedAt(LocalDateTime.now());

        if (!StringUtils.hasText(oldPinnedNotice.getStatus())) {
            oldPinnedNotice.setStatus(STATUS_APPROVED);
        } else {
            oldPinnedNotice.setStatus(normalizeApprovalStatus(oldPinnedNotice.getStatus()));
        }

        Notice savedOldPinnedNotice = repository.save(oldPinnedNotice);

        List<String> autoUnpinnedNoticeIds = new ArrayList<>();

        if (savedOldPinnedNotice != null && StringUtils.hasText(savedOldPinnedNotice.getId())) {
            autoUnpinnedNoticeIds.add(savedOldPinnedNotice.getId());
        }

        return autoUnpinnedNoticeIds;
    }

    public boolean existsByDepartmentId(String departmentId) {
        return repository.existsByDepartmentId(departmentId);
    }
}
