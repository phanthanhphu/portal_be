package org.bsl.portal.controller;

import org.bsl.portal.model.IndexRoomDisplayConfig;
import org.bsl.portal.service.IndexRoomDisplayConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/index-room-display-config")
public class IndexRoomDisplayConfigController {

    @Autowired
    private IndexRoomDisplayConfigService service;

    @GetMapping
    public ResponseEntity<?> getConfig() {
        try {
            return ResponseEntity.ok(service.getConfig());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Fetch index room display config failed: " + e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> updateConfig(@RequestBody IndexRoomDisplayConfig request) {
        try {
            return ResponseEntity.ok(service.updateConfig(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Update index room display config failed: " + e.getMessage()));
        }
    }
}
