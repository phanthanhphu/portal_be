package org.bsl.portal.controller;

import org.bsl.portal.dto.NoticeResponse;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserService userService;

    private final String FILE_DIR = "files/";

    // CREATE NOTICE
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam String userId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Boolean pinned,
            @RequestParam(required = false) MultipartFile file) {

        try {

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required"));
            }

            User user = getUserOrNull(userId);

            if (user == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User with ID " + userId + " does not exist"));
            }

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

            String fileUrl = null;

            if (file != null && !file.isEmpty()) {

                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

                Path path = Paths.get(FILE_DIR + fileName);

                Files.createDirectories(path.getParent());

                Files.write(path, file.getBytes());

                fileUrl = "/files/" + fileName;
            }

            Notice notice = new Notice();

            notice.setTitle(title.trim());
            notice.setContent(content != null ? content.trim() : "");
            notice.setUserId(userId.trim());
            notice.setDepartmentId(effectiveDepartmentId.trim());
            notice.setPinned(pinned != null ? pinned : false);
            notice.setFileUrl(fileUrl);

            LocalDateTime now = LocalDateTime.now();
            notice.setCreatedAt(now);
            notice.setUpdatedAt(now);

            Notice created = noticeService.create(notice);

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
            @RequestParam(required = false) MultipartFile file) {

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

            User user = null;
            boolean admin = false;

            if (userId != null && !userId.trim().isEmpty()) {
                user = getUserOrNull(userId.trim());

                if (user == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User with ID " + userId + " does not exist"));
                }

                admin = isAdmin(user);

                if (!canModifyNotice(user, admin, existing)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to update this notice"));
                }
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

            String fileUrl = existing.getFileUrl();

            if (file != null && !file.isEmpty()) {

                if (existing.getFileUrl() != null) {
                    Path oldPath = Paths.get(existing.getFileUrl().replace("/files/", "files/"));
                    Files.deleteIfExists(oldPath);
                }

                String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

                Path path = Paths.get(FILE_DIR + fileName);

                Files.createDirectories(path.getParent());

                Files.write(path, file.getBytes());

                fileUrl = "/files/" + fileName;
            }

            // Không mutate object existing trước khi gọi service.
            // Service cần đọc oldDepartmentId từ database để remove noticeId khỏi phòng ban cũ chính xác.
            Notice updateData = new Notice();
            updateData.setTitle(title.trim());
            updateData.setContent(content != null ? content.trim() : "");
            updateData.setUserId(existing.getUserId());
            updateData.setDepartmentId(effectiveDepartmentId.trim());
            updateData.setPinned(pinned != null ? pinned : Boolean.TRUE.equals(existing.getPinned()));
            updateData.setFileUrl(fileUrl);

            Notice updated = noticeService.update(id, updateData);

            return ResponseEntity.ok(updated);

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

            if (userId != null && !userId.trim().isEmpty()) {
                User user = getUserOrNull(userId.trim());

                if (user == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User with ID " + userId + " does not exist"));
                }

                boolean admin = isAdmin(user);

                if (!canModifyNotice(user, admin, existing)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("message", "You do not have permission to delete this notice"));
                }
            }

            if (existing.getFileUrl() != null) {

                Path path = Paths.get(existing.getFileUrl().replace("/files/", "files/"));

                Files.deleteIfExists(path);
            }

            noticeService.delete(id);

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

        Notice updated = noticeService.pin(id, Boolean.TRUE.equals(pinned));

        return ResponseEntity.ok(updated);
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            boolean admin = false;
            String currentDepartmentId = null;
            String filterDepartmentId = null;

            if (userId != null && !userId.trim().isEmpty()) {
                Optional<User> userOpt = userService.findById(userId.trim());

                if (userOpt.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User with ID " + userId + " does not exist"));
                }

                User user = userOpt.get();
                admin = isAdmin(user);
                currentDepartmentId = user.getDepartmentId();

                if (!admin && !skipDepartmentFilter) {
                    filterDepartmentId = currentDepartmentId;

                    if (filterDepartmentId == null || filterDepartmentId.trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("message", "User does not belong to any department"));
                    }
                }
            }

            /*
             * Sort by createdAt first because the screen needs the newest notice by created time:
             * - Pinned notice: newest pinned notice
             * - Priority pinned: second newest pinned notice
             */
            Sort newestSort = Sort.by(Sort.Direction.DESC, "createdAt")
                    .and(Sort.by(Sort.Direction.DESC, "updatedAt"));

            ArrayList<Map<String, Object>> pinnedNotices = new ArrayList<>();
            Map<String, Object> featuredPinnedNotice = null;
            String featuredPinnedNoticeId = null;
            Map<String, Object> priorityPinnedNotice = null;
            String priorityPinnedNoticeId = null;

            if (includeFeaturedPinned) {
                /*
                 * Simple logic: get newest notices, filter pinned=true, take first 2.
                 * This avoids returning null just because the second record in the page is not pinned.
                 */
                Pageable pinnedLookupPageable = PageRequest.of(0, 200, newestSort);

                Page<NoticeResponse> pinnedLookupResult = noticeService.search(
                        filterDepartmentId,
                        division,
                        departmentName,
                        title,
                        content,
                        pinnedLookupPageable
                );

                for (NoticeResponse notice : pinnedLookupResult.getContent()) {
                    Map<String, Object> noticeMap = toNoticeResponseMap(notice, admin, currentDepartmentId);

                    if (!Boolean.TRUE.equals(noticeMap.get("pinned"))) {
                        continue;
                    }

                    pinnedNotices.add(noticeMap);

                    if (pinnedNotices.size() == 2) {
                        break;
                    }
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

            int safePage = Math.max(page, 0);
            int safeSize = Math.max(size, 1);

            /*
             * Load a little more than the requested page because pinned notices are removed
             * from the normal content list to avoid duplicate display.
             */
            int lookupSize = ((safePage + 1) * safeSize) + pinnedNotices.size() + 10;

            Pageable lookupPageable = PageRequest.of(0, lookupSize, newestSort);

            Page<NoticeResponse> result = noticeService.search(
                    filterDepartmentId,
                    division,
                    departmentName,
                    title,
                    content,
                    lookupPageable
            );

            ArrayList<Map<String, Object>> filteredContent = new ArrayList<>();

            for (NoticeResponse notice : result.getContent()) {
                Map<String, Object> noticeMap = toNoticeResponseMap(notice, admin, currentDepartmentId);
                String noticeId = getStringValue(noticeMap.get("id"));

                if (Objects.equals(featuredPinnedNoticeId, noticeId)
                        || Objects.equals(priorityPinnedNoticeId, noticeId)) {
                    continue;
                }

                filteredContent.add(noticeMap);
            }

            int fromIndex = Math.min(safePage * safeSize, filteredContent.size());
            int toIndex = Math.min(fromIndex + safeSize, filteredContent.size());

            ArrayList<Map<String, Object>> responseContent = new ArrayList<>(filteredContent.subList(fromIndex, toIndex));

            long totalElements = Math.max(result.getTotalElements() - pinnedNotices.size(), 0);
            int totalPages = (int) Math.ceil((double) totalElements / safeSize);

            Map<String, Object> response = new HashMap<>();
            response.put("pinnedNotices", pinnedNotices);
            response.put("featuredPinnedNotice", featuredPinnedNotice);
            response.put("priorityPinnedNotice", priorityPinnedNotice);
            response.put("content", responseContent);
            response.put("isAdmin", admin);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("skipDepartmentFilter", skipDepartmentFilter);
            response.put("includeFeaturedPinned", includeFeaturedPinned);
            response.put("disableDepartmentSearch", !admin && !skipDepartmentFilter);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);
            response.put("number", page);
            response.put("size", size);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Search failed: " + e.getMessage()));
        }
    }

    private Map<String, Object> toNoticeResponseMap(
            NoticeResponse notice,
            boolean admin,
            String currentDepartmentId
    ) {
        Map<String, Object> map = new HashMap<>();

        try {
            PropertyDescriptor[] descriptors = Introspector
                    .getBeanInfo(notice.getClass(), Object.class)
                    .getPropertyDescriptors();

            for (PropertyDescriptor descriptor : descriptors) {
                Method readMethod = descriptor.getReadMethod();

                if (readMethod != null) {
                    map.put(descriptor.getName(), readMethod.invoke(notice));
                }
            }
        } catch (Exception ignored) {
        }

        String noticeDepartmentId = getStringValue(map.get("departmentId"));
        boolean canModify = admin || sameDepartment(currentDepartmentId, noticeDepartmentId);

        map.put("canEdit", canModify);
        map.put("canDelete", canModify);

        return map;
    }

    private User getUserOrNull(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        Optional<User> userOpt = userService.findById(userId.trim());

        return userOpt.orElse(null);
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
