package org.bsl.portal.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnlinePresenceService {

    private final Set<String> onlineClientIds = ConcurrentHashMap.newKeySet();

    public int join(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            onlineClientIds.add(clientId);
        }

        return getOnlineCount();
    }

    public int leave(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            onlineClientIds.remove(clientId);
        }

        return getOnlineCount();
    }

    public int getOnlineCount() {
        return onlineClientIds.size();
    }
}
