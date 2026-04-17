package org.bsl.portal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.bsl.portal.enums.FileType;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for a single department form item.
 */
public class FormItemDTO {

    @Schema(description = "Unique form id", example = "hr-1")
    @NotBlank(message = "Form id must not be blank")
    @Size(max = 50, message = "Form id must not exceed 50 characters")
    private String id;

    @Schema(description = "Form title", example = "Đơn nghỉ phép")
    @NotBlank(message = "Form title must not be blank")
    @Size(max = 255, message = "Form title must not exceed 255 characters")
    private String title;

    @Schema(description = "Form description", example = "Biểu mẫu xin nghỉ phép dành cho nhân viên.")
    @Size(max = 1000, message = "Form description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Attachment file type")
    @NotNull(message = "File type must not be null")
    private FileType fileType;

    @Schema(description = "Download file url", example = "/api/files/forms/hr-1/download")
    @NotBlank(message = "File url must not be blank")
    @Size(max = 500, message = "File url must not exceed 500 characters")
    private String fileUrl;

    @Schema(description = "Preview file url", example = "/api/files/forms/hr-1/preview")
    @Size(max = 500, message = "Preview url must not exceed 500 characters")
    private String previewUrl;

    @Schema(description = "Created timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}