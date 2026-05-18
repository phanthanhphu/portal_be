package org.bsl.portal.common.socket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AppSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(String module, String action, String id) {
        messagingTemplate.convertAndSend(
                "/topic/app-events",
                new AppSocketEvent(
                        module,
                        action,
                        id,
                        LocalDateTime.now()
                )
        );
    }

    public void noticeChanged(String action, String id) {
        publish("NOTICE", action, id);
    }

    public void formChanged(String action, String id) {
        publish("FORM", action, id);
    }

    public void documentTypeChanged(String action, String id) {
        publish("DOCUMENT_TYPE", action, id);
    }

    public void departmentChanged(String action, String id) {
        publish("DEPARTMENT", action, id);
    }

    public void appLinkChanged(String action, String id) {
        publish("APP_LINK", action, id);
    }

    public void userChanged(String action, String id) {
        publish("USER", action, id);
    }
}