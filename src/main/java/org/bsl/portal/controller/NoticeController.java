package org.bsl.portal.controller;

import org.bsl.portal.dto.NoticeResponse;
import org.bsl.portal.common.socket.AppSocketPublisher;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.Notice;
import org.bsl.portal.model.User;
import org.bsl.portal.service.DepartmentService;
import org.bsl.portal.service.NoticeService;
import org.bsl.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Arrays;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserService userService;

    @Autowired
    private AppSocketPublisher appSocketPublisher;

    private static final int MAX_FILES = 5;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_ALL = "ALL";

    private final String FILE_DIR = "files/";

    // CREATE NOTICE
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            // Support FE cũ nếu đang gửi field name là "file"
            @RequestParam(value = "file", required = false) MultipartFile file) {

        try {

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required"));
            }

            User user = getAuthenticatedUserOrNull();

            if (user == null) {
                return unauthorizedNoticeRequest();
            }

            userId = getAuthenticatedUserId(user);
            boolean admin = isAdmin(user);

            String effectiveDepartmentId = resolveDepartmentIdForCreateOrUpdate(user, admin, departmentId);

            if (effectiveDepartmentId == null || effectiveDepartmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            Department dept = departmentService.getById(effectiveDepartmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + effectiveDepartmentId + " does not exist"));
            }

            List<MultipartFile> uploadFiles = normalizeFiles(files, file);

            if (uploadFiles.size() > MAX_FILES) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "You can upload maximum " + MAX_FILES + " files"));
            }

            List<String> savedFileUrls = new ArrayList<>();

            try {
                for (MultipartFile uploadFile : uploadFiles) {
                    savedFileUrls.add(saveFile(uploadFile));
                }
            } catch (Exception e) {
                for (String savedUrl : savedFileUrls) {
                    deleteFileByUrl(savedUrl);
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Failed to save files: " + e.getMessage()));
            }

            Notice notice = new Notice();

            notice.setTitle(title.trim());
            notice.setContent(content != null ? content.trim() : "");
            notice.setUserId(userId.trim());
            notice.setDepartmentId(effectiveDepartmentId.trim());
            notice.setPinned(pinned != null ? pinned : false);
            syncFileFields(notice, savedFileUrls);

            LocalDateTime now = LocalDateTime.now();

            // Approval workflow:
            // - Admin tạo bài: duyệt luôn để hiện ngoài index.
            // - User thường tạo bài: chờ admin duyệt, chưa hiện ngoài index.
            // Every new notice must go through approval, including notices created by Admin.
            String initialStatus = STATUS_PENDING;
            notice.setStatus(initialStatus);

            if (STATUS_APPROVED.equals(initialStatus)) {
                notice.setApprovedBy(userId.trim());
                notice.setApprovedAt(now);
            }

            notice.setCreatedAt(now);
            notice.setUpdatedAt(now);

            Notice created = noticeService.create(notice);

            appSocketPublisher.noticeChanged("CREATED", created.getId());

            return ResponseEntity.ok(created);

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Create failed: " + e.getMessage()));
        }
    }

    // UPDATE NOTICE
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Boolean pinned,
            /*
             * FE mới gửi danh sách file cũ CÒN GIỮ LẠI.
             * Ví dụ cũ có [file1, file2], user xóa file1
             * thì FE gửi fileUrls = [file2].
             */
            @RequestParam(value = "fileUrls", required = false) List<String> keepFileUrls,
            /*
             * FE cũ hoặc UI dạng remove riêng có thể gửi danh sách file muốn xóa.
             */
            @RequestParam(value = "removeFileUrls", required = false) List<String> removeFileUrls,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            // Support FE cũ nếu đang gửi field name là "file"
            @RequestParam(value = "file", required = false) MultipartFile file) {

        try {

            Notice existing = noticeService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required"));
            }

            User user = getAuthenticatedUserOrNull();

            if (user == null) {
                return unauthorizedNoticeRequest();
            }

            boolean admin = isAdmin(user);
            userId = getAuthenticatedUserId(user);

            if (!canModifyNotice(user, admin, existing)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have permission to update this notice"));
            }

            String effectiveDepartmentId;

            if (user != null && !admin) {
                String userDepartmentId = user.getDepartmentId() != null ? user.getDepartmentId().trim() : null;
                String requestedDepartmentId = departmentId != null ? departmentId.trim() : null;

                // User thường không được chuyển notice qua phòng ban khác.
                // Trả lỗi rõ ràng thay vì âm thầm ép về phòng ban của user, vì điều đó làm FE tưởng đã đổi phòng nhưng data không đổi.
                if (requestedDepartmentId != null
                        && !requestedDepartmentId.isEmpty()
                        && userDepartmentId != null
                        && !requestedDepartmentId.equals(userDepartmentId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "Only admin can move notice to another department"));
                }

                effectiveDepartmentId = userDepartmentId;
            } else {
                // Admin hoặc request nội bộ không có userId: dùng departmentId FE gửi lên; nếu rỗng thì giữ phòng ban hiện tại.
                effectiveDepartmentId = departmentId != null && !departmentId.trim().isEmpty()
                        ? departmentId.trim()
                        : existing.getDepartmentId();
            }

            if (effectiveDepartmentId == null || effectiveDepartmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            Department dept = departmentService.getById(effectiveDepartmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + effectiveDepartmentId + " does not exist"));
            }

            List<String> oldFileUrls = getExistingFileUrls(existing);
            List<String> keepUrlsFromRequest = normalizeFileUrls(keepFileUrls);
            List<String> currentFileUrls = new ArrayList<>();

            /*
             * FE mới chỉ gửi fileUrls = danh sách file cũ CÒN GIỮ LẠI.
             * Ví dụ cũ [1, 2], user xóa 1 thì FE gửi fileUrls = [2].
             * Backend tự xóa khỏi source file nào cũ nhưng không còn trong fileUrls.
             */
            if (keepFileUrls != null) {
                for (String keepUrl : keepUrlsFromRequest) {
                    if (oldFileUrls.contains(keepUrl)) {
                        currentFileUrls.add(keepUrl);
                    }
                }

                for (String oldUrl : oldFileUrls) {
                    if (!currentFileUrls.contains(oldUrl)) {
                        deleteFileByUrl(oldUrl);
                    }
                }
            } else {
                // Tương thích FE cũ: nếu không gửi fileUrls thì giữ nguyên file cũ
                currentFileUrls = new ArrayList<>(oldFileUrls);
            }

            // Tương thích FE cũ nếu vẫn gửi removeFileUrls
            List<String> removeUrls = normalizeFileUrls(removeFileUrls);

            if (!removeUrls.isEmpty()) {
                List<String> remainingUrls = new ArrayList<>();

                for (String currentUrl : currentFileUrls) {
                    if (removeUrls.contains(currentUrl)) {
                        deleteFileByUrl(currentUrl);
                    } else {
                        remainingUrls.add(currentUrl);
                    }
                }

                currentFileUrls = remainingUrls;
            }

            List<MultipartFile> uploadFiles = normalizeFiles(files, file);

            if (currentFileUrls.size() + uploadFiles.size() > MAX_FILES) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "You can upload maximum " + MAX_FILES + " files"));
            }

            List<String> newSavedUrls = new ArrayList<>();

            try {
                for (MultipartFile uploadFile : uploadFiles) {
                    String savedUrl = saveFile(uploadFile);
                    newSavedUrls.add(savedUrl);
                    currentFileUrls.add(savedUrl);
                }
            } catch (Exception e) {
                for (String savedUrl : newSavedUrls) {
                    deleteFileByUrl(savedUrl);
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Failed to save new files: " + e.getMessage()));
            }

            // Không mutate object existing trước khi gọi service.
            // Service cần đọc oldDepartmentId từ database để remove noticeId khỏi phòng ban cũ chính xác.
            Notice updateData = new Notice();
            updateData.setTitle(title.trim());
            updateData.setContent(content != null ? content.trim() : "");
            updateData.setUserId(existing.getUserId());
            updateData.setDepartmentId(effectiveDepartmentId.trim());
            updateData.setPinned(pinned != null ? pinned : Boolean.TRUE.equals(existing.getPinned()));
            syncFileFields(updateData, currentFileUrls);

            String nextStatus = normalizeApprovalStatus(existing.getStatus());

            // Nếu user thường sửa bài, bài cần quay lại trạng thái chờ duyệt.
            // Admin sửa bài thì giữ trạng thái hiện tại.
            if (user != null && !admin) {
                nextStatus = STATUS_PENDING;
                updateData.setApprovedBy(null);
                updateData.setApprovedAt(null);
                updateData.setRejectedBy(null);
                updateData.setRejectedAt(null);
                updateData.setRejectReason(null);
            } else {
                updateData.setApprovedBy(existing.getApprovedBy());
                updateData.setApprovedAt(existing.getApprovedAt());
                updateData.setRejectedBy(existing.getRejectedBy());
                updateData.setRejectedAt(existing.getRejectedAt());
                updateData.setRejectReason(existing.getRejectReason());
            }

            updateData.setStatus(nextStatus);

            NoticeService.PinNoticeResult updateResult = noticeService.updateWithAutoUnpinIfApprovedPinned(id, updateData);

            if (updateResult == null || updateResult.getNotice() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            Notice updated = updateResult.getNotice();
            List<String> autoUnpinnedNoticeIds = updateResult.getAutoUnpinnedNoticeIds();

            appSocketPublisher.noticeChanged("UPDATED", updated.getId());

            for (String autoUnpinnedNoticeId : autoUnpinnedNoticeIds) {
                appSocketPublisher.noticeChanged("UPDATED", autoUnpinnedNoticeId);
            }

            appSocketPublisher.noticeChanged("PINNED_CHANGED", updated.getId());

            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Auto-Unpinned-Notice-Ids", String.join(",", autoUnpinnedNoticeIds));

            return new ResponseEntity<>(updated, headers, HttpStatus.OK);

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update failed: " + e.getMessage()));
        }
    }

    // DELETE NOTICE
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @RequestParam(required = false) String userId) {

        Notice existing = noticeService.getById(id);

        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notice not found"));
        }

        try {

            User user = getAuthenticatedUserOrNull();

            if (user == null) {
                return unauthorizedNoticeRequest();
            }

            boolean admin = isAdmin(user);
            userId = getAuthenticatedUserId(user);

            if (!canModifyNotice(user, admin, existing)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have permission to delete this notice"));
            }

            List<String> fileUrls = getExistingFileUrls(existing);

            for (String fileUrl : fileUrls) {
                deleteFileByUrl(fileUrl);
            }

            noticeService.delete(id);

            appSocketPublisher.noticeChanged("DELETED", id);

            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));

        } catch (Exception e) {

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Delete failed: " + e.getMessage()));
        }
    }

    // GET NOTICE BY ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {

        Notice notice = noticeService.getById(id);

        if (notice == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notice not found"));
        }

        return ResponseEntity.ok(notice);
    }

    // GET ALL PAGED
    @GetMapping
    public ResponseEntity<?> getAllPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<Notice> result = noticeService.getAllPaged(page, size);

        return ResponseEntity.ok(result);
    }

    // PIN NOTICE
    @PatchMapping("/{id}/pin")
    public ResponseEntity<?> pin(
            @PathVariable String id,
            @RequestParam Boolean pinned) {

        Notice notice = noticeService.getById(id);

        if (notice == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notice not found"));
        }

        User user = getAuthenticatedUserOrNull();

        if (user == null) {
            return unauthorizedNoticeRequest();
        }

        boolean admin = isAdmin(user);

        if (!canModifyNotice(user, admin, notice)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have permission to pin this notice"));
        }

        NoticeService.PinNoticeResult pinResult = noticeService.pinWithAutoUnpin(
                id,
                Boolean.TRUE.equals(pinned)
        );

        if (pinResult == null || pinResult.getNotice() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Notice not found"));
        }

        Notice updated = pinResult.getNotice();
        List<String> autoUnpinnedNoticeIds = pinResult.getAutoUnpinnedNoticeIds();

        /*
         * Rất quan trọng cho UI realtime:
         * Khi ghim notice mới và backend tự bỏ ghim notice cũ, phải báo socket cho cả
         * notice mới và các notice bị auto-unpin. Nếu chỉ báo notice mới thì FE có thể
         * vẫn hiển thị checkbox pinned của notice cũ là đang chọn.
         */
        for (String autoUnpinnedNoticeId : autoUnpinnedNoticeIds) {
            appSocketPublisher.noticeChanged("UPDATED", autoUnpinnedNoticeId);
        }

        appSocketPublisher.noticeChanged("UPDATED", updated.getId());
        appSocketPublisher.noticeChanged("PINNED_CHANGED", updated.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Auto-Unpinned-Notice-Ids", String.join(",", autoUnpinnedNoticeIds));

        // Giữ response body là Notice như cũ để không làm vỡ FE hiện tại.
        return ResponseEntity.ok()
                .headers(headers)
                .body(updated);
    }

    // APPROVE NOTICE
    @PatchMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable String id,
            @RequestParam(required = false) String userId) {

        try {
            Notice existing = noticeService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            User adminUser = getAuthenticatedUserOrNull();

            if (adminUser == null) {
                return unauthorizedNoticeRequest();
            }

            if (!canApproveNoticeByPermission(adminUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have Notice approval permission"));
            }

            userId = getAuthenticatedUserId(adminUser);

            LocalDateTime now = LocalDateTime.now();

            existing.setStatus(STATUS_APPROVED);
            existing.setApprovedBy(userId.trim());
            existing.setApprovedAt(now);

            existing.setRejectedBy(null);
            existing.setRejectedAt(null);
            existing.setRejectReason(null);

            existing.setUpdatedAt(now);

            NoticeService.PinNoticeResult approveResult = noticeService.saveWithAutoUnpinIfApprovedPinned(existing);

            if (approveResult == null || approveResult.getNotice() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            Notice updated = approveResult.getNotice();
            List<String> autoUnpinnedNoticeIds = approveResult.getAutoUnpinnedNoticeIds();

            appSocketPublisher.noticeChanged("APPROVED", updated.getId());

            for (String autoUnpinnedNoticeId : autoUnpinnedNoticeIds) {
                appSocketPublisher.noticeChanged("UPDATED", autoUnpinnedNoticeId);
            }

            appSocketPublisher.noticeChanged("PINNED_CHANGED", updated.getId());

            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Auto-Unpinned-Notice-Ids", String.join(",", autoUnpinnedNoticeIds));

            return new ResponseEntity<>(updated, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Approve failed: " + e.getMessage()));
        }
    }

    // REJECT NOTICE
    @PatchMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable String id,
            @RequestParam(required = false) String userId,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            Notice existing = noticeService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            User adminUser = getAuthenticatedUserOrNull();

            if (adminUser == null) {
                return unauthorizedNoticeRequest();
            }

            if (!canApproveNoticeByPermission(adminUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have Notice approval permission"));
            }

            userId = getAuthenticatedUserId(adminUser);

            String reason = body != null ? body.getOrDefault("reason", "") : "";
            LocalDateTime now = LocalDateTime.now();

            existing.setStatus(STATUS_REJECTED);
            existing.setRejectedBy(userId.trim());
            existing.setRejectedAt(now);
            existing.setRejectReason(reason != null ? reason.trim() : "");

            existing.setApprovedBy(null);
            existing.setApprovedAt(null);

            existing.setUpdatedAt(now);

            Notice updated = noticeService.save(existing);

            appSocketPublisher.noticeChanged("REJECTED", updated.getId());

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Reject failed: " + e.getMessage()));
        }
    }


    // CHANGE NOTICE STATUS
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(
            @PathVariable String id,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            Notice existing = noticeService.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            User adminUser = getAuthenticatedUserOrNull();

            if (adminUser == null) {
                return unauthorizedNoticeRequest();
            }

            if (!canApproveNoticeByPermission(adminUser)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have Notice approval permission"));
            }

            userId = getAuthenticatedUserId(adminUser);

            String requestedStatus = status;
            String rejectReason = reason;

            if (body != null) {
                if (body.get("status") != null) {
                    requestedStatus = body.get("status");
                }

                if (body.get("reason") != null) {
                    rejectReason = body.get("reason");
                }
            }

            String nextStatus = normalizeApprovalStatusFilter(requestedStatus);

            if (STATUS_ALL.equals(nextStatus)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid notice status"));
            }

            if (STATUS_REJECTED.equals(nextStatus)
                    && (rejectReason == null || rejectReason.trim().isEmpty())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Reject reason is required"));
            }

            LocalDateTime now = LocalDateTime.now();

            if (STATUS_PENDING.equals(nextStatus)) {
                existing.setStatus(STATUS_PENDING);
                existing.setApprovedBy(null);
                existing.setApprovedAt(null);
                existing.setRejectedBy(null);
                existing.setRejectedAt(null);
                existing.setRejectReason(null);
            } else if (STATUS_APPROVED.equals(nextStatus)) {
                existing.setStatus(STATUS_APPROVED);
                existing.setApprovedBy(userId.trim());
                existing.setApprovedAt(now);
                existing.setRejectedBy(null);
                existing.setRejectedAt(null);
                existing.setRejectReason(null);
            } else if (STATUS_REJECTED.equals(nextStatus)) {
                existing.setStatus(STATUS_REJECTED);
                existing.setRejectedBy(userId.trim());
                existing.setRejectedAt(now);
                existing.setRejectReason(rejectReason.trim());
                existing.setApprovedBy(null);
                existing.setApprovedAt(null);
            }

            existing.setUpdatedAt(now);

            NoticeService.PinNoticeResult statusResult = noticeService.saveWithAutoUnpinIfApprovedPinned(existing);

            if (statusResult == null || statusResult.getNotice() == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Notice not found"));
            }

            Notice updated = statusResult.getNotice();
            List<String> autoUnpinnedNoticeIds = statusResult.getAutoUnpinnedNoticeIds();

            appSocketPublisher.noticeChanged(nextStatus, updated.getId());

            for (String autoUnpinnedNoticeId : autoUnpinnedNoticeIds) {
                appSocketPublisher.noticeChanged("UPDATED", autoUnpinnedNoticeId);
            }

            appSocketPublisher.noticeChanged("PINNED_CHANGED", updated.getId());

            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Auto-Unpinned-Notice-Ids", String.join(",", autoUnpinnedNoticeIds));

            return new ResponseEntity<>(updated, headers, HttpStatus.OK);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Change status failed: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(defaultValue = "false") boolean includeFeaturedPinned,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content,
            @RequestParam(defaultValue = "APPROVED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            boolean admin = false;
            boolean view = false;
            boolean canApproveNotice = false;
            String currentDepartmentId = null;
            String currentUserName = null;
            String filterDepartmentId = null;
            String normalizedStatusFilter = normalizeApprovalStatusFilter(status);

            User user = getAuthenticatedUserOrNull();

            // Public search is allowed only for approved notices.
            if (user == null && !STATUS_APPROVED.equals(normalizedStatusFilter)) {
                normalizedStatusFilter = STATUS_APPROVED;
            }

            if (user != null) {
                userId = getAuthenticatedUserId(user);
                admin = isAdmin(user);
                view = isViewRole(user);
                canApproveNotice = canApproveNoticeByPermission(user);
                currentDepartmentId = user.getDepartmentId();
                currentUserName = getUserDisplayName(user);

                if (!STATUS_APPROVED.equals(normalizedStatusFilter) && !canApproveNotice && !view) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to view non-approved notices"));
                }

                if (!admin && !view && !skipDepartmentFilter) {
                    filterDepartmentId = currentDepartmentId;

                    if (filterDepartmentId == null || filterDepartmentId.trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("message", "User does not belong to any department"));
                    }
                }
            }

            Sort newestSort = Sort.by(Sort.Direction.DESC, "createdAt")
                    .and(Sort.by(Sort.Direction.DESC, "updatedAt"));

            int safePage = Math.max(page, 0);
            int safeSize = Math.max(size, 1);

            Map<String, String> userNameCache = new HashMap<>();
            Map<String, Department> departmentCache = new HashMap<>();

            ArrayList<Map<String, Object>> pinnedNotices = new ArrayList<>();
            Map<String, Object> featuredPinnedNotice = null;
            String featuredPinnedNoticeId = null;
            Map<String, Object> priorityPinnedNotice = null;
            String priorityPinnedNoticeId = null;
            List<String> excludedNoticeIds = new ArrayList<>();

            if (includeFeaturedPinned) {
                // Chỉ lấy đúng 2 pinned notice ngay từ MongoDB, không lấy 1000 rồi lọc trong Java.
                Pageable pinnedLookupPageable = PageRequest.of(0, 2, newestSort);

                Page<Notice> pinnedLookupResult = noticeService.searchPinnedNoticeEntities(
                        filterDepartmentId,
                        division,
                        departmentName,
                        title,
                        content,
                        normalizedStatusFilter,
                        pinnedLookupPageable
                );

                for (Notice notice : pinnedLookupResult.getContent()) {
                    Map<String, Object> noticeMap = toNoticeResponseMap(
                            notice,
                            admin,
                            view,
                            currentDepartmentId,
                            userNameCache,
                            departmentCache
                    );

                    pinnedNotices.add(noticeMap);
                    excludedNoticeIds.add(notice.getId());
                }

                if (!pinnedNotices.isEmpty()) {
                    featuredPinnedNotice = pinnedNotices.get(0);
                    featuredPinnedNoticeId = getStringValue(featuredPinnedNotice.get("id"));
                }

                if (pinnedNotices.size() > 1) {
                    priorityPinnedNotice = pinnedNotices.get(1);
                    priorityPinnedNoticeId = getStringValue(priorityPinnedNotice.get("id"));
                }
            }

            Pageable lookupPageable = PageRequest.of(safePage, safeSize, newestSort);

            // Lọc status + phân trang + bỏ pinned duplicate ngay trong MongoDB.
            Page<Notice> result = noticeService.searchNoticeEntitiesExcludingIds(
                    filterDepartmentId,
                    division,
                    departmentName,
                    title,
                    content,
                    normalizedStatusFilter,
                    excludedNoticeIds,
                    lookupPageable
            );

            ArrayList<Map<String, Object>> responseContent = new ArrayList<>();

            for (Notice notice : result.getContent()) {
                responseContent.add(toNoticeResponseMap(
                        notice,
                        admin,
                        view,
                        currentDepartmentId,
                        userNameCache,
                        departmentCache
                ));
            }

            long totalElements = result.getTotalElements();
            int totalPages = Math.max(1, result.getTotalPages());

            Map<String, Object> response = new HashMap<>();
            response.put("featuredPinnedNotice", featuredPinnedNotice);
            response.put("priorityPinnedNotice", priorityPinnedNotice);
            response.put("pinnedNotices", pinnedNotices);
            response.put("pinnedNoticeCount", pinnedNotices.size());
            response.put("content", responseContent);
            response.put("isAdmin", admin);
            response.put("isView", view);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("currentUserName", currentUserName);
            response.put("requestUserName", currentUserName);
            response.put("skipDepartmentFilter", skipDepartmentFilter);
            response.put("includeFeaturedPinned", includeFeaturedPinned);
            response.put("status", normalizedStatusFilter);
            String approvePermission = user != null ? normalizeApprovePermission(user.getApprovePermission()) : "NONE";
            response.put("approvePermission", approvePermission);
            response.put("approvePermissions", toApprovePermissionList(approvePermission));
            response.put("canApproveNotice", canApproveNotice);
            response.put("disableDepartmentSearch", !admin && !view && !skipDepartmentFilter);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);
            response.put("number", safePage);
            response.put("size", safeSize);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Search failed: " + e.getMessage()));
        }
    }

    @GetMapping("/status-counts")
    public ResponseEntity<?> getStatusCounts(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String content) {

        try {
            boolean admin = false;
            boolean view = false;
            boolean canApproveNotice = false;
            String currentDepartmentId = null;
            String currentUserName = null;
            String filterDepartmentId = null;

            User user = getAuthenticatedUserOrNull();

            if (user == null) {
                return unauthorizedNoticeRequest();
            }

            userId = getAuthenticatedUserId(user);
            admin = isAdmin(user);
            view = isViewRole(user);
            canApproveNotice = canApproveNoticeByPermission(user);
            currentDepartmentId = user.getDepartmentId();
            currentUserName = getUserDisplayName(user);

            if (!canApproveNotice && !view) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have permission to view notice approval counts"));
            }

            if (!admin && !view && !skipDepartmentFilter) {
                filterDepartmentId = currentDepartmentId;

                if (filterDepartmentId == null || filterDepartmentId.trim().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User does not belong to any department"));
                }
            }

            long pendingCount = noticeService.countNoticeEntities(
                    filterDepartmentId,
                    division,
                    departmentName,
                    title,
                    content,
                    STATUS_PENDING
            );

            long approvedCount = noticeService.countNoticeEntities(
                    filterDepartmentId,
                    division,
                    departmentName,
                    title,
                    content,
                    STATUS_APPROVED
            );

            long rejectedCount = noticeService.countNoticeEntities(
                    filterDepartmentId,
                    division,
                    departmentName,
                    title,
                    content,
                    STATUS_REJECTED
            );

            Map<String, Object> counts = new HashMap<>();
            counts.put(STATUS_PENDING, pendingCount);
            counts.put(STATUS_APPROVED, approvedCount);
            counts.put(STATUS_REJECTED, rejectedCount);

            Map<String, Object> response = new HashMap<>();
            response.put("counts", counts);
            response.put("pending", pendingCount);
            response.put("approved", approvedCount);
            response.put("rejected", rejectedCount);
            response.put("isAdmin", admin);
            response.put("isView", view);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("currentUserName", currentUserName);
            response.put("requestUserName", currentUserName);
            response.put("skipDepartmentFilter", skipDepartmentFilter);
            String approvePermission = normalizeApprovePermission(user.getApprovePermission());
            response.put("approvePermission", approvePermission);
            response.put("approvePermissions", toApprovePermissionList(approvePermission));
            response.put("canApproveNotice", canApproveNotice);
            response.put("disableDepartmentSearch", !admin && !view && !skipDepartmentFilter);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Load notice status counts failed: " + e.getMessage()));
        }
    }


    private Map<String, Object> toNoticeResponseMap(
            Notice notice,
            boolean admin,
            boolean readOnly,
            String currentDepartmentId,
            Map<String, String> userNameCache,
            Map<String, Department> departmentCache
    ) {
        Map<String, Object> map = new HashMap<>();

        if (notice == null) {
            return map;
        }

        map.put("id", notice.getId());
        map.put("title", notice.getTitle());
        map.put("content", notice.getContent());
        map.put("pinned", Boolean.TRUE.equals(notice.getPinned()));
        map.put("userId", notice.getUserId());
        map.put("departmentId", notice.getDepartmentId());
        map.put("createdAt", notice.getCreatedAt());
        map.put("updatedAt", notice.getUpdatedAt());
        map.put("status", normalizeApprovalStatus(notice.getStatus()));
        map.put("approvedBy", notice.getApprovedBy());
        map.put("approvedAt", notice.getApprovedAt());
        map.put("rejectedBy", notice.getRejectedBy());
        map.put("rejectedAt", notice.getRejectedAt());
        map.put("rejectReason", notice.getRejectReason());

        String noticeDepartmentId = getStringValue(notice.getDepartmentId());

        if (noticeDepartmentId != null) {
            Department dept = departmentCache.computeIfAbsent(
                    noticeDepartmentId,
                    departmentService::getById
            );

            if (dept != null) {
                map.put("departmentName", dept.getDepartmentName());
                map.put("division", dept.getDivision());
            }
        }

        String creatorUserId = getStringValue(notice.getUserId());
        String creatorUserName = null;

        if (creatorUserId != null) {
            creatorUserName = userNameCache.computeIfAbsent(
                    creatorUserId,
                    this::getUserDisplayNameById
            );
        }

        map.put("createdByUserId", creatorUserId);
        map.put("createdByName", creatorUserName != null ? creatorUserName : creatorUserId);
        map.put("userName", creatorUserName != null ? creatorUserName : creatorUserId);

        List<String> fileUrls = getExistingFileUrls(notice);
        List<String> previewUrls = normalizeFileUrls(notice.getPreviewUrls());

        if (previewUrls.isEmpty()) {
            previewUrls = new ArrayList<>(fileUrls);
        }

        map.put("fileUrl", fileUrls.isEmpty() ? null : fileUrls.get(0));
        map.put("previewUrl", previewUrls.isEmpty() ? map.get("fileUrl") : previewUrls.get(0));
        map.put("fileUrls", fileUrls);
        map.put("previewUrls", previewUrls);

        boolean canModify = !readOnly && (admin || sameDepartment(currentDepartmentId, noticeDepartmentId));

        map.put("canEdit", canModify);
        map.put("canDelete", canModify);

        return map;
    }


    private List<MultipartFile> normalizeFiles(List<MultipartFile> files, MultipartFile oldSingleFile) {
        List<MultipartFile> result = new ArrayList<>();

        if (files != null) {
            for (MultipartFile uploadFile : files) {
                if (uploadFile != null && !uploadFile.isEmpty()) {
                    result.add(uploadFile);
                }
            }
        }

        if (oldSingleFile != null && !oldSingleFile.isEmpty()) {
            result.add(oldSingleFile);
        }

        return result;
    }

    private List<String> normalizeFileUrls(List<String> urls) {
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

    private List<String> getExistingFileUrls(Notice notice) {
        List<String> result = new ArrayList<>();

        if (notice == null) {
            return result;
        }

        if (notice.getFileUrls() != null) {
            for (String url : notice.getFileUrls()) {
                if (url != null && !url.trim().isEmpty() && !result.contains(url.trim())) {
                    result.add(url.trim());
                }
            }
        }

        // Support data cũ chỉ có 1 fileUrl
        if (result.isEmpty() && notice.getFileUrl() != null && !notice.getFileUrl().trim().isEmpty()) {
            result.add(notice.getFileUrl().trim());
        }

        return result;
    }

    private String saveFile(MultipartFile file) throws Exception {
        String originalName = file.getOriginalFilename();
        String safeOriginalName = originalName == null || originalName.trim().isEmpty()
                ? "file"
                : Paths.get(originalName).getFileName().toString();

        Path uploadDir = Paths.get(FILE_DIR).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String timeCode = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = buildTimeCodeFileName(uploadDir, safeOriginalName, timeCode);
        Path filePath = uploadDir.resolve(fileName).normalize();

        if (!filePath.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }

        Files.write(filePath, file.getBytes());

        return "/files/" + fileName;
    }

    private String buildTimeCodeFileName(Path uploadDir, String originalName, String timeCode) {
        String cleanName = sanitizeFileName(originalName);
        String baseName = getBaseName(cleanName);
        String extension = getExtension(cleanName);

        String fileName = baseName + "_" + timeCode + extension;
        Path filePath = uploadDir.resolve(fileName).normalize();

        int counter = 1;

        while (Files.exists(filePath)) {
            fileName = baseName + "_" + timeCode + "_" + counter + extension;
            filePath = uploadDir.resolve(fileName).normalize();
            counter++;
        }

        return fileName;
    }

    private String sanitizeFileName(String fileName) {
        String cleanName = fileName == null || fileName.trim().isEmpty()
                ? "file"
                : fileName.trim();

        cleanName = cleanName.replaceAll("[\\\\/:*?\"<>|]", "_");
        cleanName = cleanName.replaceAll("\\s+", " ");

        return cleanName.isBlank() ? "file" : cleanName;
    }

    private String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex <= 0) {
            return fileName;
        }

        String baseName = fileName.substring(0, dotIndex).trim();

        return baseName.isEmpty() ? "file" : baseName;
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex);
    }

    private void deleteFileByUrl(String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                return;
            }

            if (!fileUrl.startsWith("/files/")) {
                return;
            }

            String fileName = fileUrl.replace("/files/", "");

            Path uploadDir = Paths.get(FILE_DIR).toAbsolutePath().normalize();
            Path filePath = uploadDir.resolve(fileName).normalize();

            if (filePath.startsWith(uploadDir)) {
                Files.deleteIfExists(filePath);
            }

        } catch (Exception ignored) {
            // Không cho lỗi xóa file vật lý làm fail toàn bộ API
        }
    }

    private void syncFileFields(Notice notice, List<String> fileUrls) {
        List<String> cleanUrls = normalizeFileUrls(fileUrls);

        notice.setFileUrls(cleanUrls);
        notice.setPreviewUrls(new ArrayList<>(cleanUrls));

        // Giữ field cũ để FE cũ không lỗi
        if (!cleanUrls.isEmpty()) {
            notice.setFileUrl(cleanUrls.get(0));
            notice.setPreviewUrl(cleanUrls.get(0));
        } else {
            notice.setFileUrl(null);
            notice.setPreviewUrl(null);
        }
    }

    private User getAuthenticatedUserOrNull() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return null;
            }

            Object principal = authentication.getPrincipal();

            if (principal == null) {
                return null;
            }

            String email = String.valueOf(principal).trim();

            if (email.isEmpty() || "anonymousUser".equalsIgnoreCase(email)) {
                return null;
            }

            return userService.findByEmail(email).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private ResponseEntity<?> unauthorizedNoticeRequest() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Authentication is required. Please login again."));
    }

    private String getAuthenticatedUserId(User user) {
        return user != null && user.getId() != null ? user.getId().trim() : "";
    }

    /**
     * Normalize approve permissions from checkbox UI.
     *
     * FE/DB values accepted:
     * - NONE
     * - NOTICE
     * - DOCUMENT
     * - NOTICE,DOCUMENT
     *
     * Backward compatibility:
     * - BOTH and ALL are treated as NOTICE,DOCUMENT.
     */
    private String normalizeApprovePermission(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "NONE";
        }

        String normalized = value.trim().toUpperCase();
        Set<String> permissions = new LinkedHashSet<>();

        for (String item : normalized.split(",")) {
            String cleanItem = item.trim();

            if ("BOTH".equals(cleanItem) || "ALL".equals(cleanItem)) {
                permissions.add("NOTICE");
                permissions.add("DOCUMENT");
            } else if ("NOTICE".equals(cleanItem) || "DOCUMENT".equals(cleanItem)) {
                permissions.add(cleanItem);
            }
        }

        if (permissions.isEmpty()) {
            return "NONE";
        }

        return String.join(",", permissions);
    }

    private List<String> toApprovePermissionList(String value) {
        String normalized = normalizeApprovePermission(value);

        if ("NONE".equals(normalized)) {
            return List.of("NONE");
        }

        return Arrays.asList(normalized.split(","));
    }

    private boolean hasApprovePermission(String permissionValue, String target) {
        String normalized = normalizeApprovePermission(permissionValue);

        if ("NONE".equals(normalized)) {
            return false;
        }

        return Arrays.asList(normalized.split(",")).contains(target);
    }

    private boolean canApproveNoticeByPermission(User user) {
        if (user == null) {
            return false;
        }

        // Notice approval is controlled by approvePermission only.
        // Role Admin alone does not bypass this check.
        return hasApprovePermission(user.getApprovePermission(), "NOTICE");
    }

    private User getUserOrNull(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        Optional<User> userOpt = userService.findById(userId.trim());

        return userOpt.orElse(null);
    }

    private String getUserDisplayNameById(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        try {
            Optional<User> userOpt = userService.findById(userId.trim());

            if (userOpt.isEmpty()) {
                return userId.trim();
            }

            String displayName = getUserDisplayName(userOpt.get());

            return displayName != null ? displayName : userId.trim();
        } catch (Exception e) {
            return userId.trim();
        }
    }

    private String getUserDisplayName(User user) {
        if (user == null) {
            return null;
        }

        String firstName = getUserStringProperty(user, "firstName");
        String lastName = getUserStringProperty(user, "lastName");
        String fullFromParts = String.join(" ",
                firstName != null ? firstName : "",
                lastName != null ? lastName : "").trim();

        if (!fullFromParts.isEmpty()) {
            return fullFromParts;
        }

        String[] preferredFields = {
                "fullName",
                "name",
                "username",
                "userName",
                "displayName",
                "email"
        };

        for (String field : preferredFields) {
            String value = getUserStringProperty(user, field);

            if (value != null) {
                return value;
            }
        }

        return getStringValue(getUserStringProperty(user, "id"));
    }

    private String getUserStringProperty(User user, String propertyName) {
        try {
            PropertyDescriptor[] descriptors = Introspector
                    .getBeanInfo(user.getClass(), Object.class)
                    .getPropertyDescriptors();

            for (PropertyDescriptor descriptor : descriptors) {
                if (!descriptor.getName().equalsIgnoreCase(propertyName)) {
                    continue;
                }

                Method readMethod = descriptor.getReadMethod();

                if (readMethod == null) {
                    return null;
                }

                Object value = readMethod.invoke(user);

                return getStringValue(value);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String resolveDepartmentIdForCreateOrUpdate(User user, boolean admin, String requestDepartmentId) {
        if (admin) {
            return requestDepartmentId != null ? requestDepartmentId.trim() : null;
        }

        return user.getDepartmentId() != null ? user.getDepartmentId().trim() : null;
    }

    private boolean canModifyNotice(User user, boolean admin, Notice notice) {
        if (admin) {
            return true;
        }

        if (user == null || notice == null) {
            return false;
        }

        return sameDepartment(user.getDepartmentId(), notice.getDepartmentId());
    }

    private String getStringValue(Object value) {
        if (value == null) {
            return null;
        }

        String result = String.valueOf(value).trim();

        return result.isEmpty() ? null : result;
    }

    private boolean sameDepartment(String currentDepartmentId, String noticeDepartmentId) {
        if (currentDepartmentId == null || noticeDepartmentId == null) {
            return false;
        }

        return currentDepartmentId.trim().equals(noticeDepartmentId.trim());
    }

    private String normalizeApprovalStatus(Object value) {
        if (value == null) {
            // Dữ liệu cũ chưa có status thì xem như đã duyệt,
            // tránh làm mất bài cũ ngoài trang index.
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
        if (value == null || value.trim().isEmpty()) {
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

    private boolean matchesApprovalStatus(Map<String, Object> noticeMap, String statusFilter) {
        if (STATUS_ALL.equals(statusFilter)) {
            return true;
        }

        String itemStatus = normalizeApprovalStatus(noticeMap.get("status"));

        return statusFilter.equals(itemStatus);
    }

    private boolean isViewRole(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }

        String role = user.getRole().trim();
        return "VIEW".equalsIgnoreCase(role) || "ROLE_VIEW".equalsIgnoreCase(role);
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
}
