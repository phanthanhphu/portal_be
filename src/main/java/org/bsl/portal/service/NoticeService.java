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

    @Autowired
    private NoticeRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private NoticeDisplayOrderService displayOrderService;

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
    public synchronized Notice create(Notice notice) {
        displayOrderService.ensureInitialized();

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
        displayOrderService.reconcile(null, created, null, false);

        // Add notice id vào department.noticeIds sau khi MongoDB đã tạo id.
        departmentService.addNoticeToDepartment(
                created.getDepartmentId(),
                created.getId()
        );

        return created;
    }

    // Status/approval workflow and edit both pass through the same ordering logic.
    public synchronized Notice save(Notice notice) {
        return saveWithAutoUnpinIfApprovedPinned(notice).getNotice();
    }

    public synchronized PinNoticeResult saveWithAutoUnpinIfApprovedPinned(Notice notice) {
        if (notice == null) return null;
        displayOrderService.ensureInitialized();
        Notice previous = notice.getId() == null ? null : repository.findById(notice.getId()).orElse(null);
        // A controller may hold a pre-migration entity without the new rank fields.
        // Preserve its database ranks when the caller only changes approval/status.
        if (previous != null && java.util.Objects.equals(previous.getPinned(), notice.getPinned())) {
            if (notice.getPinnedOrder() == null) notice.setPinnedOrder(previous.getPinnedOrder());
            if (notice.getDisplayOrder() == null) notice.setDisplayOrder(previous.getDisplayOrder());
        }
        LocalDateTime now = LocalDateTime.now();
        if (notice.getCreatedAt() == null) notice.setCreatedAt(now);
        notice.setUpdatedAt(now);
        notice.setStatus(normalizeApprovalStatus(notice.getStatus()));
        Notice saved = repository.save(notice);
        departmentService.addNoticeToDepartment(saved.getDepartmentId(), saved.getId());
        NoticeDisplayOrderService.Result result = displayOrderService.reconcile(
                previous, saved, null, false);
        return new PinNoticeResult(result.getNotice(), result.getDemotedIds());
    }

    public synchronized PinNoticeResult updateWithAutoUnpinIfApprovedPinned(String id, Notice data) {
        return updateReordered(id, data);
    }

    // UPDATE
    public synchronized Notice update(String id, Notice data) {
        PinNoticeResult result = updateReordered(id, data);
        return result == null ? null : result.getNotice();
    }

    private synchronized PinNoticeResult updateReordered(String id, Notice data) {
        displayOrderService.ensureInitialized();
        Optional<Notice> optional = repository.findById(id);

        if (optional.isEmpty()) {
            return null;
        }

        Notice notice = optional.get();
        Notice previous = repository.findById(id).orElse(null);
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
        if (Boolean.TRUE.equals(data.getPinned())) {
            // An order selected by Admin in the edit dialog takes priority.
            if (data.getPinnedOrder() != null) notice.setPinnedOrder(data.getPinnedOrder());
        } else {
            notice.setPinnedOrder(null);
        }
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
        Integer requestedPin = Boolean.TRUE.equals(updated.getPinned())
                && (previous == null || !Boolean.TRUE.equals(previous.getPinned())
                    || !java.util.Objects.equals(previous.getPinnedOrder(), updated.getPinnedOrder()))
                ? updated.getPinnedOrder() : null;
        NoticeDisplayOrderService.Result orderResult = displayOrderService.reconcile(
                previous, updated, requestedPin, false);

        // Nếu đổi phòng ban: remove khỏi department cũ, add vào department mới.
        // Nếu không đổi phòng ban: vẫn đảm bảo id đã có trong department.noticeIds.
        departmentService.moveNoticeDepartment(
                oldDepartmentId,
                updated.getDepartmentId(),
                updated.getId()
        );

        return new PinNoticeResult(orderResult.getNotice(), orderResult.getDemotedIds());
    }

    // DELETE
    public synchronized void delete(String id) {
        displayOrderService.ensureInitialized();
        Notice existing = repository.findById(id).orElse(null);

        if (existing == null) {
            return;
        }

        String departmentId = existing.getDepartmentId();
        String noticeId = existing.getId();

        repository.deleteById(id);
        displayOrderService.afterDelete();

        // Remove notice id khỏi department.noticeIds khi xóa notice.
        departmentService.removeNoticeFromDepartment(departmentId, noticeId);
    }

    // GET BY ID
    public Notice getById(String id) {
        displayOrderService.ensureInitialized();
        return repository.findById(id).orElse(null);
    }

    // GET ALL PAGED
    public Page<Notice> getAllPaged(int page, int size) {
        displayOrderService.ensureInitialized();

        Pageable pageable = PageRequest.of(
                page,
                size,
                noticeDisplaySort()
        );

        return repository.findAll(pageable);
    }

    /** Persistent display order. Pinned: 1,2; then Normal: 1..N. */
    public Sort noticeDisplaySort() {
        return Sort.by(Sort.Direction.DESC, "pinned")
                .and(Sort.by(Sort.Direction.ASC, "pinnedOrder"))
                .and(Sort.by(Sort.Direction.ASC, "displayOrder"))
                .and(Sort.by(Sort.Direction.DESC, "createdAt"));
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
        displayOrderService.ensureInitialized();
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
        displayOrderService.ensureInitialized();
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
        displayOrderService.ensureInitialized();
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
        displayOrderService.ensureInitialized();
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
        displayOrderService.ensureInitialized();
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
        displayOrderService.ensureInitialized();
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
        displayOrderService.ensureInitialized();
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
        query.with(noticeDisplaySort());
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
        displayOrderService.ensureInitialized();
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
        response.setPinnedOrder(notice.getPinnedOrder());
        response.setDisplayOrder(notice.getDisplayOrder());
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

    // Legacy pin endpoint: new pin goes to position #1 by default.
    public Notice pin(String id, Boolean pinned) {
        PinNoticeResult result = pinWithAutoUnpin(id, pinned);
        return result != null ? result.getNotice() : null;
    }

    public synchronized PinNoticeResult pinWithAutoUnpin(String id, Boolean pinned) {
        displayOrderService.ensureInitialized();
        Notice previous = repository.findById(id).orElse(null);
        if (previous == null) return null;
        Notice current = repository.findById(id).orElse(null);
        current.setPinned(Boolean.TRUE.equals(pinned));
        if (Boolean.TRUE.equals(pinned) && !Boolean.TRUE.equals(previous.getPinned())) {
            current.setPinnedOrder(1);
        } else if (!Boolean.TRUE.equals(pinned)) {
            current.setPinnedOrder(null);
        }
        current.setUpdatedAt(LocalDateTime.now());
        Notice saved = repository.save(current);
        departmentService.addNoticeToDepartment(saved.getDepartmentId(), saved.getId());
        NoticeDisplayOrderService.Result result = displayOrderService.reconcile(previous, saved, null, false);
        return new PinNoticeResult(result.getNotice(), result.getDemotedIds());
    }

    /** Admin-only position adjustment, with automatic overflow to Normal #1. */
    public synchronized PinNoticeResult changeDisplayOrder(String id, boolean pinned, int order) {
        displayOrderService.ensureInitialized();
        Notice previous = repository.findById(id).orElse(null);
        if (previous == null) return null;
        if (!"APPROVED".equalsIgnoreCase(previous.getStatus())) {
            throw new IllegalArgumentException("Only approved notices can be ordered");
        }
        if (order < 1 || (pinned && order > 2)) {
            throw new IllegalArgumentException("Pinned order must be 1 or 2; normal order must be positive");
        }
        Notice current = repository.findById(id).orElse(null);
        current.setPinned(pinned);
        current.setPinnedOrder(pinned ? order : null);
        // Do not change updatedAt when only moving a notice.
        Notice saved = repository.save(current);
        NoticeDisplayOrderService.Result result = displayOrderService.reconcile(previous, saved, order, true);
        return new PinNoticeResult(result.getNotice(), result.getDemotedIds());
    }

    public boolean existsByDepartmentId(String departmentId) {
        return repository.existsByDepartmentId(departmentId);
    }
}
