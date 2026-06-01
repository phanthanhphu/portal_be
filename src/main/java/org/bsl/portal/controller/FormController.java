package org.bsl.portal.controller;

import org.bsl.portal.common.socket.AppSocketPublisher;
import org.bsl.portal.dto.FormResponse;
import org.bsl.portal.model.Department;
import org.bsl.portal.model.DocumentType;
import org.bsl.portal.model.FormItem;
import org.bsl.portal.model.User;
import org.bsl.portal.service.DepartmentService;
import org.bsl.portal.service.DocumentTypeService;
import org.bsl.portal.service.FormService;
import org.bsl.portal.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/forms")
class FormController {

    @Autowired
    private FormService service;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private DocumentTypeService documentTypeService;

    @Autowired
    private UserService userService;

    @Autowired
    private AppSocketPublisher appSocketPublisher;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final int MAX_FILES = 5;
    private final String FILE_DIR = "files/";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_ALL = "ALL";

    private static final String APPROVE_NONE = "NONE";
    private static final String APPROVE_NOTICE = "NOTICE";
    private static final String APPROVE_DOCUMENT = "DOCUMENT";
    private static final String APPROVE_BOTH = "BOTH";

    // ==================== CREATE FORM ====================
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String departmentId,
            @RequestParam String typeId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            // Support FE cũ nếu đang gửi field name là "file"
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        try {
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required and cannot be empty"));
            }

            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            if (typeId == null || typeId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Type ID is required"));
            }

            Department dept = departmentService.getById(departmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            DocumentType type = documentTypeService.getById(typeId.trim());
            if (type == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Document type with ID " + typeId + " does not exist"));
            }

            if (service.existsByTitleAndDepartmentIdAndTypeId(
                    title.trim(),
                    departmentId.trim(),
                    typeId.trim()
            )) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title '" + title.trim() + "' already exists in this department and type"));
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

            FormItem item = new FormItem();
            item.setTitle(title.trim());
            item.setDescription(description != null ? description.trim() : "");
            item.setDepartmentId(departmentId.trim());
            item.setTypeId(typeId.trim());

            syncFileFields(item, savedFileUrls);

            LocalDateTime now = LocalDateTime.now();
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setStatus(STATUS_PENDING);

            FormItem created = service.create(item);

            appSocketPublisher.formChanged("CREATED", created.getId());

            return ResponseEntity.ok(created);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Create failed: " + e.getMessage()));
        }
    }

    // ==================== UPDATE FORM ====================
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String departmentId,
            @RequestParam String typeId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            // Support FE cũ nếu đang gửi field name là "file"
            @RequestParam(value = "file", required = false) MultipartFile file,
            /*
             * FE mới gửi danh sách fileUrl CÒN GIỮ LẠI.
             * Ví dụ hiện có [1, 2], user xóa 1 thì FE gửi fileUrls = [2].
             * Backend sẽ tự so sánh với file cũ trong DB:
             * - file cũ không còn trong fileUrls => delete khỏi source
             * - file còn trong fileUrls => giữ lại
             */
            @RequestParam(value = "fileUrls", required = false) List<String> keepFileUrls,
            /*
             * Field cũ giữ lại để tương thích nếu FE cũ vẫn gửi removeFileUrls.
             * FE mới không cần dùng field này nữa.
             */
            @RequestParam(value = "removeFileUrls", required = false) List<String> removeFileUrls
    ) {
        try {
            FormItem existing = service.getById(id);
            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Form not found"));
            }

            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title is required and cannot be empty"));
            }

            if (departmentId == null || departmentId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department ID is required"));
            }

            if (typeId == null || typeId.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Type ID is required"));
            }

            Department dept = departmentService.getById(departmentId.trim());
            if (dept == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Department with ID " + departmentId + " does not exist"));
            }

            DocumentType type = documentTypeService.getById(typeId.trim());
            if (type == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Document type with ID " + typeId + " does not exist"));
            }

            String newTitle = title.trim();
            String newDepartmentId = departmentId.trim();
            String newTypeId = typeId.trim();

            boolean titleChanged = !newTitle.equals(existing.getTitle());

            boolean departmentChanged = existing.getDepartmentId() == null
                    || !newDepartmentId.equals(existing.getDepartmentId());

            boolean typeChanged = existing.getTypeId() == null
                    || !newTypeId.equals(existing.getTypeId());

            if ((titleChanged || departmentChanged || typeChanged)
                    && service.existsByTitleAndDepartmentIdAndTypeId(newTitle, newDepartmentId, newTypeId)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Title '" + newTitle + "' already exists in this department and type"));
            }

            existing.setTitle(newTitle);
            existing.setDescription(description != null ? description.trim() : "");
            existing.setDepartmentId(newDepartmentId);
            existing.setTypeId(newTypeId);
            existing.setUpdatedAt(LocalDateTime.now());

            List<String> oldFileUrls = getExistingFileUrls(existing);

            /*
             * FE mới chỉ gửi fileUrls = danh sách file cũ CÒN GIỮ LẠI.
             * Nếu FE có gửi fileUrls thì backend lấy đó làm source of truth.
             * Nếu FE không gửi fileUrls thì fallback giữ nguyên toàn bộ file cũ.
             */
            List<String> keepUrlsFromRequest = normalizeFileUrls(keepFileUrls);
            List<String> currentFileUrls = new ArrayList<>();

            if (keepFileUrls != null) {
                for (String keepUrl : keepUrlsFromRequest) {
                    if (oldFileUrls.contains(keepUrl)) {
                        currentFileUrls.add(keepUrl);
                    }
                }

                // File cũ không còn nằm trong fileUrls thì xóa khỏi source
                for (String oldUrl : oldFileUrls) {
                    if (!currentFileUrls.contains(oldUrl)) {
                        deleteFileByUrl(oldUrl);
                    }
                }
            } else {
                // Tương thích FE cũ: không gửi fileUrls thì giữ nguyên file cũ
                currentFileUrls = new ArrayList<>(oldFileUrls);
            }

            /*
             * Tương thích FE cũ nếu vẫn gửi removeFileUrls.
             * FE mới không cần dùng field này.
             */
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

            syncFileFields(existing, currentFileUrls);

            FormItem updated = service.update(id, existing);

            appSocketPublisher.formChanged("UPDATED", updated.getId());

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update failed: " + e.getMessage()));
        }
    }

    // ==================== DELETE FORM ====================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            FormItem existing = service.getById(id);

            if (existing != null) {
                List<String> fileUrls = getExistingFileUrls(existing);

                for (String fileUrl : fileUrls) {
                    deleteFileByUrl(fileUrl);
                }
            }

            service.delete(id);

            appSocketPublisher.formChanged("DELETED", id);

            return ResponseEntity.ok(Map.of("message", "Deleted successfully"));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Delete failed: " + e.getMessage()));
        }
    }

    // ==================== GET ALL ====================
    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(service.getAll());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to fetch forms: " + e.getMessage()));
        }
    }

    // ==================== GET BY ID ====================
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        FormItem form = service.getById(id);

        if (form == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Form not found"));
        }

        return ResponseEntity.ok(form);
    }

    // ==================== GET BY DEPARTMENT ====================
    @GetMapping("/department/{departmentId}")
    public ResponseEntity<?> getByDepartment(@PathVariable String departmentId) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "DepartmentId is required"));
        }

        return ResponseEntity.ok(service.getByDepartment(departmentId.trim()));
    }

    // ==================== GET BY TYPE ====================
    @GetMapping("/type/{typeId}")
    public ResponseEntity<?> getByType(@PathVariable String typeId) {
        if (typeId == null || typeId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "TypeId is required"));
        }

        return ResponseEntity.ok(service.getByTypeId(typeId.trim()));
    }

    // ==================== SEARCH ====================
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean skipDepartmentFilter,
            @RequestParam(required = false) String division,
            @RequestParam(required = false) String departmentName,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String typeId,
            @RequestParam(defaultValue = "APPROVED") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User currentUser = null;
            boolean admin = false;
            String currentDepartmentId = null;
            String filterDepartmentId = null;
            String statusFilter = normalizeApprovalStatusFilter(status);
            boolean canApproveDocument = false;

            int safePage = Math.max(page, 0);
            int safeSize = Math.max(size, 1);

            if (userId != null && !userId.trim().isEmpty()) {
                Optional<User> userOpt = userService.findById(userId.trim());

                if (userOpt.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User with ID " + userId + " does not exist"));
                }

                currentUser = userOpt.get();
                admin = isAdmin(currentUser);
                currentDepartmentId = currentUser.getDepartmentId();
                canApproveDocument = canApproveDocument(currentUser);

                if (!admin && !skipDepartmentFilter) {
                    if (currentDepartmentId == null || currentDepartmentId.trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("message", "User does not belong to any department"));
                    }

                    filterDepartmentId = currentDepartmentId.trim();
                }
            }

            Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));

            /*
             * IMPORTANT FIX:
             * Status is stored on FormItem, but service.search(...) returns FormResponse and
             * does not filter status in DB. The old code filtered only the current page after
             * pagination, so totalElements/counts were wrong.
             *
             * This code loads all pages that match normal filters first, applies the status
             * filter, then paginates manually. That makes Pending/Approved/Rejected counts and
             * tabs consistent with the UI.
             */
            final int lookupSize = 500;
            int lookupPage = 0;
            int lookupTotalPages = 1;
            List<Map<String, Object>> filteredContent = new ArrayList<>();

            do {
                Pageable lookupPageable = PageRequest.of(lookupPage, lookupSize, sort);

                Page<FormResponse> lookupResult = service.search(
                        filterDepartmentId,
                        division,
                        departmentName,
                        title,
                        description,
                        typeId,
                        lookupPageable
                );

                lookupTotalPages = Math.max(lookupResult.getTotalPages(), 1);

                for (FormResponse form : lookupResult.getContent()) {
                    Map<String, Object> formMap = toFormResponseMap(form, admin, currentDepartmentId);

                    if (matchesApprovalStatus(formMap, statusFilter)) {
                        filteredContent.add(formMap);
                    }
                }

                lookupPage++;
            } while (lookupPage < lookupTotalPages);

            int totalElements = filteredContent.size();
            int fromIndex = Math.min(safePage * safeSize, totalElements);
            int toIndex = Math.min(fromIndex + safeSize, totalElements);
            List<Map<String, Object>> pageContent = new ArrayList<>(filteredContent.subList(fromIndex, toIndex));
            int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);

            Map<String, Object> response = new HashMap<>();
            response.put("content", pageContent);
            response.put("isAdmin", admin);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("skipDepartmentFilter", skipDepartmentFilter);
            response.put("disableDepartmentSearch", !admin && !skipDepartmentFilter);
            response.put("status", statusFilter);
            response.put("canApproveDocument", canApproveDocument);
            response.put("approvePermission", currentUser != null ? normalizeApprovePermission(currentUser.getApprovePermission()) : APPROVE_NONE);
            response.put("totalElements", totalElements);
            response.put("totalPages", totalPages);
            response.put("number", safePage);
            response.put("size", safeSize);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to search forms: " + e.getMessage()));
        }
    }


    private String getDocumentTypeDisplayName(String typeId) {
        if (typeId == null || typeId.trim().isEmpty()) {
            return null;
        }

        try {
            DocumentType type = documentTypeService.getById(typeId.trim());

            if (type == null) {
                return null;
            }

            String[] getterNames = {
                    "getName",
                    "getTypeName",
                    "getDocumentTypeName",
                    "getTitle"
            };

            for (String getterName : getterNames) {
                try {
                    Object value = type.getClass().getMethod(getterName).invoke(type);

                    if (value != null && !String.valueOf(value).trim().isEmpty()) {
                        return String.valueOf(value).trim();
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try the next common getter name.
                }
            }
        } catch (Exception ignored) {
            // Keep API stable: if type lookup fails, FE can still receive typeId.
        }

        return null;
    }

    private Map<String, Object> toFormResponseMap(
            FormResponse form,
            boolean admin,
            String currentDepartmentId
    ) {
        Map<String, Object> map = new HashMap<>();

        String formDepartmentId = form.getDepartmentId();
        String formTypeId = form.getTypeId();
        String formTypeName = getDocumentTypeDisplayName(formTypeId);
        boolean canModify = admin || sameDepartment(currentDepartmentId, formDepartmentId);

        /*
         * IMPORTANT:
         * service.search(...) returns FormResponse. In many projects this DTO is built
         * by aggregation/projection and may only contain fileUrl/previewUrl, not the
         * full fileUrls/previewUrls arrays from MongoDB.
         *
         * Therefore, for the search list response, load the full FormItem by id first.
         * This guarantees API /api/forms/search returns the real fileUrls array.
         */
        FormItem fullItem = null;

        if (form.getId() != null && !form.getId().trim().isEmpty()) {
            fullItem = service.getById(form.getId().trim());
        }

        List<String> fileUrls = new ArrayList<>();

        // Priority 1: real data from MongoDB FormItem.fileUrls
        if (fullItem != null) {
            fileUrls = getExistingFileUrls(fullItem);
        }

        // Priority 2: data from FormResponse.fileUrls if service/search already supports it
        if (fileUrls.isEmpty()) {
            fileUrls = normalizeFileUrls(form.getFileUrls());
        }

        // Priority 3: old data only has one fileUrl
        if (fileUrls.isEmpty() && form.getFileUrl() != null && !form.getFileUrl().trim().isEmpty()) {
            fileUrls.add(form.getFileUrl().trim());
        }

        List<String> previewUrls = new ArrayList<>();

        // Priority 1: real data from MongoDB FormItem.previewUrls
        if (fullItem != null) {
            previewUrls = normalizeFileUrls(fullItem.getPreviewUrls());
        }

        // Priority 2: data from FormResponse.previewUrls if service/search already supports it
        if (previewUrls.isEmpty()) {
            previewUrls = normalizeFileUrls(form.getPreviewUrls());
        }

        // Priority 3: use fileUrls as previewUrls fallback
        if (previewUrls.isEmpty()) {
            previewUrls = new ArrayList<>(fileUrls);
        }

        String firstFileUrl = fileUrls.isEmpty() ? null : fileUrls.get(0);
        String firstPreviewUrl = previewUrls.isEmpty() ? firstFileUrl : previewUrls.get(0);

        if (fullItem != null) {
            map.put("status", normalizeApprovalStatus(fullItem.getStatus()));
            map.put("approvedBy", fullItem.getApprovedBy());
            map.put("approvedAt", fullItem.getApprovedAt());
            map.put("rejectedBy", fullItem.getRejectedBy());
            map.put("rejectedAt", fullItem.getRejectedAt());
            map.put("rejectReason", fullItem.getRejectReason());
        } else {
            map.put("status", STATUS_APPROVED);
            map.put("approvedBy", null);
            map.put("approvedAt", null);
            map.put("rejectedBy", null);
            map.put("rejectedAt", null);
            map.put("rejectReason", null);
        }

        map.put("id", form.getId());
        map.put("departmentId", formDepartmentId);
        map.put("typeId", formTypeId);
        map.put("typeName", formTypeName != null ? formTypeName : formTypeId);
        map.put("documentTypeName", formTypeName != null ? formTypeName : formTypeId);
        map.put("departmentName", form.getDepartmentName());
        map.put("division", form.getDivision());
        map.put("title", form.getTitle());
        map.put("description", form.getDescription());

        // Prefer fileType from search DTO, fallback to full MongoDB item
        map.put("fileType", form.getFileType() != null
                ? form.getFileType()
                : fullItem != null ? fullItem.getFileType() : null);

        // Old fields: keep first file for backward compatibility
        map.put("fileUrl", firstFileUrl);
        map.put("previewUrl", firstPreviewUrl);

        // New fields: FE must use these arrays
        map.put("fileUrls", fileUrls);
        map.put("previewUrls", previewUrls);

        map.put("createdAt", fullItem != null ? fullItem.getCreatedAt() : form.getCreatedAt());
        map.put("updatedAt", fullItem != null ? fullItem.getUpdatedAt() : form.getUpdatedAt());

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

    private List<String> getExistingFileUrls(FormItem item) {
        List<String> result = new ArrayList<>();

        if (item == null) {
            return result;
        }

        if (item.getFileUrls() != null) {
            for (String url : item.getFileUrls()) {
                if (url != null && !url.trim().isEmpty() && !result.contains(url.trim())) {
                    result.add(url.trim());
                }
            }
        }

        // Support data cũ chỉ có 1 fileUrl
        if (result.isEmpty() && item.getFileUrl() != null && !item.getFileUrl().trim().isEmpty()) {
            result.add(item.getFileUrl().trim());
        }

        return result;
    }

    private String saveFile(MultipartFile file) throws Exception {
        String originalName = file.getOriginalFilename();
        String safeOriginalName = originalName == null || originalName.trim().isEmpty()
                ? "file"
                : Paths.get(originalName).getFileName().toString();

        String cleanOriginalName = safeOriginalName
                .replaceAll("[\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleanOriginalName.isEmpty()) {
            cleanOriginalName = "file";
        }

        int dotIndex = cleanOriginalName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? cleanOriginalName.substring(0, dotIndex) : cleanOriginalName;
        String extension = dotIndex > 0 ? cleanOriginalName.substring(dotIndex) : "";

        String timeCode = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = baseName + "_" + timeCode + extension;

        Path uploadDir = Paths.get(FILE_DIR).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        Path filePath = uploadDir.resolve(fileName).normalize();
        int duplicateIndex = 1;

        while (Files.exists(filePath)) {
            fileName = baseName + "_" + timeCode + "_" + duplicateIndex + extension;
            filePath = uploadDir.resolve(fileName).normalize();
            duplicateIndex++;
        }

        if (!filePath.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }

        Files.write(filePath, file.getBytes());

        return "/files/" + fileName;
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

    private void syncFileFields(FormItem item, List<String> fileUrls) {
        List<String> cleanUrls = normalizeFileUrls(fileUrls);

        item.setFileUrls(cleanUrls);
        item.setPreviewUrls(new ArrayList<>(cleanUrls));

        // Giữ field cũ để FE cũ không lỗi
        if (!cleanUrls.isEmpty()) {
            item.setFileUrl(cleanUrls.get(0));
            item.setPreviewUrl(cleanUrls.get(0));
        } else {
            item.setFileUrl(null);
            item.setPreviewUrl(null);
        }
    }

    private FormItem saveApprovalDirectly(FormItem item) {
        if (item == null) {
            return null;
        }

        if (item.getCreatedAt() == null) {
            item.setCreatedAt(LocalDateTime.now());
        }

        if (item.getStatus() == null || item.getStatus().trim().isEmpty()) {
            item.setStatus(STATUS_APPROVED);
        } else {
            item.setStatus(normalizeApprovalStatus(item.getStatus()));
        }

        return mongoTemplate.save(item);
    }

    // ==================== APPROVE FORM/DOCUMENT ====================
    @PatchMapping("/{id}/approve")
    public ResponseEntity<?> approve(
            @PathVariable String id,
            @RequestParam String userId) {

        try {
            Optional<User> userOpt = userService.findById(userId.trim());

            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User with ID " + userId + " does not exist"));
            }

            User user = userOpt.get();

            if (!canApproveDocument(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have Document approval permission"));
            }

            FormItem existing = service.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Form not found"));
            }

            LocalDateTime now = LocalDateTime.now();

            existing.setStatus(STATUS_APPROVED);
            existing.setApprovedBy(userId.trim());
            existing.setApprovedAt(now);
            existing.setRejectedBy(null);
            existing.setRejectedAt(null);
            existing.setRejectReason(null);
            existing.setUpdatedAt(now);

            FormItem updated = saveApprovalDirectly(existing);

            appSocketPublisher.formChanged("APPROVED", updated.getId());

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Approve failed: " + e.getMessage()));
        }
    }

    // ==================== REJECT FORM/DOCUMENT ====================
    @PatchMapping("/{id}/reject")
    public ResponseEntity<?> reject(
            @PathVariable String id,
            @RequestParam String userId,
            @RequestBody(required = false) Map<String, String> body) {

        try {
            Optional<User> userOpt = userService.findById(userId.trim());

            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User with ID " + userId + " does not exist"));
            }

            User user = userOpt.get();

            if (!canApproveDocument(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have Document approval permission"));
            }

            FormItem existing = service.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Form not found"));
            }

            String reason = body != null ? body.getOrDefault("reason", "") : "";
            LocalDateTime now = LocalDateTime.now();

            existing.setStatus(STATUS_REJECTED);
            existing.setRejectedBy(userId.trim());
            existing.setRejectedAt(now);
            existing.setRejectReason(reason != null ? reason.trim() : "");
            existing.setApprovedBy(null);
            existing.setApprovedAt(null);
            existing.setUpdatedAt(now);

            FormItem updated = saveApprovalDirectly(existing);

            appSocketPublisher.formChanged("REJECTED", updated.getId());

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Reject failed: " + e.getMessage()));
        }
    }

    // ==================== CHANGE FORM/DOCUMENT STATUS ====================
    @PatchMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(
            @PathVariable String id,
            @RequestParam String userId,
            @RequestBody Map<String, String> body) {

        try {
            Optional<User> userOpt = userService.findById(userId.trim());

            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "User with ID " + userId + " does not exist"));
            }

            User user = userOpt.get();

            if (!canApproveDocument(user)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "You do not have Document approval permission"));
            }

            FormItem existing = service.getById(id);

            if (existing == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Form not found"));
            }

            String nextStatus = normalizeApprovalStatus(body != null ? body.get("status") : null);
            String reason = body != null ? body.getOrDefault("reason", "") : "";
            LocalDateTime now = LocalDateTime.now();

            existing.setStatus(nextStatus);
            existing.setUpdatedAt(now);

            if (STATUS_APPROVED.equals(nextStatus)) {
                existing.setApprovedBy(userId.trim());
                existing.setApprovedAt(now);
                existing.setRejectedBy(null);
                existing.setRejectedAt(null);
                existing.setRejectReason(null);
            } else if (STATUS_REJECTED.equals(nextStatus)) {
                if (reason == null || reason.trim().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Reject reason is required"));
                }

                existing.setRejectedBy(userId.trim());
                existing.setRejectedAt(now);
                existing.setRejectReason(reason.trim());
                existing.setApprovedBy(null);
                existing.setApprovedAt(null);
            } else {
                existing.setApprovedBy(null);
                existing.setApprovedAt(null);
                existing.setRejectedBy(null);
                existing.setRejectedAt(null);
                existing.setRejectReason(null);
            }

            FormItem updated = saveApprovalDirectly(existing);

            appSocketPublisher.formChanged("STATUS_CHANGED", updated.getId());

            return ResponseEntity.ok(updated);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Change status failed: " + e.getMessage()));
        }
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

    private boolean matchesApprovalStatus(Map<String, Object> map, String statusFilter) {
        if (STATUS_ALL.equals(statusFilter)) {
            return true;
        }

        String itemStatus = normalizeApprovalStatus(map.get("status"));

        return statusFilter.equals(itemStatus);
    }

    private String normalizeApprovePermission(String value) {
        if (value == null || value.trim().isEmpty()) {
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

    private boolean canApproveDocument(User user) {
        String permission = normalizeApprovePermission(user != null ? user.getApprovePermission() : null);

        return APPROVE_DOCUMENT.equals(permission) || APPROVE_BOTH.equals(permission);
    }

    private boolean sameDepartment(String currentDepartmentId, String itemDepartmentId) {
        if (currentDepartmentId == null || itemDepartmentId == null) {
            return false;
        }

        return currentDepartmentId.trim().equals(itemDepartmentId.trim());
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
