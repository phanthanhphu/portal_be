package org.bsl.portal.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    private static final int MAX_FILES = 5;
    private final String FILE_DIR = "files/";

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

            FormItem created = service.create(item);

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        try {
            User currentUser = null;
            boolean admin = false;
            String currentDepartmentId = null;
            String filterDepartmentId = null;

            if (userId != null && !userId.trim().isEmpty()) {
                Optional<User> userOpt = userService.findById(userId.trim());

                if (userOpt.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "User with ID " + userId + " does not exist"));
                }

                currentUser = userOpt.get();
                admin = isAdmin(currentUser);
                currentDepartmentId = currentUser.getDepartmentId();

                if (!admin && !skipDepartmentFilter) {
                    if (currentDepartmentId == null || currentDepartmentId.trim().isEmpty()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("message", "User does not belong to any department"));
                    }

                    filterDepartmentId = currentDepartmentId.trim();
                }
            }

            Pageable pageable = PageRequest.of(
                    page,
                    size,
                    Sort.by(Sort.Direction.DESC, "updatedAt")
                            .and(Sort.by(Sort.Direction.DESC, "createdAt"))
            );

            Page<FormResponse> result = service.search(
                    filterDepartmentId,
                    division,
                    departmentName,
                    title,
                    description,
                    typeId,
                    pageable
            );

            List<Map<String, Object>> content = new ArrayList<>();

            for (FormResponse form : result.getContent()) {
                content.add(toFormResponseMap(form, admin, currentDepartmentId));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("isAdmin", admin);
            response.put("currentDepartmentId", currentDepartmentId);
            response.put("skipDepartmentFilter", skipDepartmentFilter);
            response.put("disableDepartmentSearch", !admin && !skipDepartmentFilter);
            response.put("totalElements", result.getTotalElements());
            response.put("totalPages", result.getTotalPages());
            response.put("number", result.getNumber());
            response.put("size", result.getSize());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to search forms: " + e.getMessage()));
        }
    }

    private Map<String, Object> toFormResponseMap(
            FormResponse form,
            boolean admin,
            String currentDepartmentId
    ) {
        Map<String, Object> map = new HashMap<>();

        String formDepartmentId = form.getDepartmentId();
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

        map.put("id", form.getId());
        map.put("departmentId", formDepartmentId);
        map.put("typeId", form.getTypeId());
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

        map.put("createdAt", form.getCreatedAt());
        map.put("updatedAt", form.getUpdatedAt());

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

        String fileName = System.currentTimeMillis()
                + "_"
                + UUID.randomUUID()
                + "_"
                + safeOriginalName;

        Path uploadDir = Paths.get(FILE_DIR).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        Path filePath = uploadDir.resolve(fileName).normalize();

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
