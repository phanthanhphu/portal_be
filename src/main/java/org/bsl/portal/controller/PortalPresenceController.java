package org.bsl.portal.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.bsl.portal.common.socket.AppSocketPublisher;
import org.bsl.portal.service.OnlinePresenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/portal-presence")
public class PortalPresenceController {

    private final OnlinePresenceService onlinePresenceService;
    private final AppSocketPublisher appSocketPublisher;

    @GetMapping("/online-count")
    public Map<String, Object> getOnlineCount() {
        return response(onlinePresenceService.getOnlineCount());
    }

    @PostMapping("/join")
    public Map<String, Object> join(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        String clientId = resolveClientId(body, request);

        int count = onlinePresenceService.join(clientId);
        appSocketPublisher.onlineCountChanged(count);

        return response(count);
    }

    @PostMapping("/leave")
    public Map<String, Object> leave(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        String clientId = resolveClientId(body, request);

        int count = onlinePresenceService.leave(clientId);
        appSocketPublisher.onlineCountChanged(count);

        return response(count);
    }

    private Map<String, Object> response(int onlineCount) {
        return Map.of(
                "onlineCount", onlineCount,
                "timestamp", LocalDateTime.now()
        );
    }

    private String resolveClientId(Map<String, Object> body, HttpServletRequest request) {
        if (body != null) {
            Object value = body.get("clientId");

            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }

        return request.getSession(true).getId();
    }
}
