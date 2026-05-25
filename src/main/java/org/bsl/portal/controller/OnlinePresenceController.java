package org.bsl.portal.controller;

import lombok.RequiredArgsConstructor;
import org.bsl.portal.service.OnlinePresenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class OnlinePresenceController {

    private final OnlinePresenceService onlinePresenceService;

    @GetMapping("/api/presence/online-count")
    public Map<String, Object> getOnlineCount() {
        return Map.of(
                "onlineCount", onlinePresenceService.getOnlineCount(),
                "timestamp", LocalDateTime.now()
        );
    }
}
