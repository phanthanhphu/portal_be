package org.bsl.portal.handler;

import lombok.RequiredArgsConstructor;
import org.bsl.portal.common.socket.AppSocketPublisher;
import org.bsl.portal.service.OnlinePresenceService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class OnlinePresenceEventListener {

    private final OnlinePresenceService onlinePresenceService;
    private final AppSocketPublisher appSocketPublisher;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        int count = onlinePresenceService.join(sessionId);
        appSocketPublisher.onlineCountChanged(count);
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        int count = onlinePresenceService.leave(sessionId);
        appSocketPublisher.onlineCountChanged(count);
    }
}
