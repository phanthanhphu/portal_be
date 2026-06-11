package org.bsl.portal.service;

import org.bsl.portal.model.IndexRoomDisplayConfig;
import org.bsl.portal.repository.IndexRoomDisplayConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class IndexRoomDisplayConfigService {

    private static final String DEFAULT_ID = "DEFAULT";

    private static final String DEFAULT_EYEBROW = "Room Reservation Display";
    private static final String DEFAULT_WELCOME = "Welcome to";
    private static final String DEFAULT_TITLE = "Broadpeak Soc Trang";
    private static final String DEFAULT_STATUS = "Reserved";

    @Autowired
    private IndexRoomDisplayConfigRepository repository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    public IndexRoomDisplayConfig getConfig() {
        return repository.findById(DEFAULT_ID)
                .orElseGet(this::buildDefaultConfig);
    }

    public IndexRoomDisplayConfig updateConfig(IndexRoomDisplayConfig request) {
        if (request == null) {
            throw new IllegalArgumentException("Display config data is required");
        }

        String eyebrowText = normalizeText(request.getEyebrowText(), DEFAULT_EYEBROW);
        String welcomeText = normalizeText(request.getWelcomeText(), DEFAULT_WELCOME);
        String titleText = normalizeText(request.getTitleText(), DEFAULT_TITLE);
        String statusText = normalizeText(request.getStatusText(), DEFAULT_STATUS);

        IndexRoomDisplayConfig existing = repository.findById(DEFAULT_ID)
                .orElseGet(this::buildDefaultConfig);

        LocalDateTime now = LocalDateTime.now();
        String updatedBy = resolveCurrentUser();

        if (existing.getCreatedAt() == null) {
            existing.setCreatedAt(now);
        }

        if (existing.getCreatedBy() == null || existing.getCreatedBy().trim().isEmpty()) {
            existing.setCreatedBy(updatedBy);
        }

        existing.setId(DEFAULT_ID);
        existing.setEyebrowText(eyebrowText);
        existing.setWelcomeText(welcomeText);
        existing.setTitleText(titleText);
        existing.setStatusText(statusText);
        existing.setUpdatedBy(updatedBy);
        existing.setUpdatedAt(now);

        IndexRoomDisplayConfig saved = repository.save(existing);

        publishConfigUpdated(saved);

        return saved;
    }

    private IndexRoomDisplayConfig buildDefaultConfig() {
        LocalDateTime now = LocalDateTime.now();

        IndexRoomDisplayConfig config = new IndexRoomDisplayConfig();
        config.setId(DEFAULT_ID);
        config.setEyebrowText(DEFAULT_EYEBROW);
        config.setWelcomeText(DEFAULT_WELCOME);
        config.setTitleText(DEFAULT_TITLE);
        config.setStatusText(DEFAULT_STATUS);
        config.setCreatedBy("SYSTEM");
        config.setUpdatedBy("SYSTEM");
        config.setCreatedAt(now);
        config.setUpdatedAt(now);

        return config;
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }

        return value.trim();
    }

    private String resolveCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()) {
                String name = authentication.getName();

                if (name != null && !name.trim().isEmpty() && !"anonymousUser".equalsIgnoreCase(name)) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
            // Fallback below.
        }

        return "SYSTEM";
    }

    private void publishConfigUpdated(IndexRoomDisplayConfig saved) {
        if (messagingTemplate == null) return;

        try {
            messagingTemplate.convertAndSend(
                    "/topic/app-events",
                    Map.of(
                            "module", "INDEX_ROOM_DISPLAY_CONFIG",
                            "action", "UPDATED",
                            "id", saved.getId()
                    )
            );
        } catch (Exception ignored) {
            // Không làm fail API nếu socket publish lỗi.
        }
    }
}
