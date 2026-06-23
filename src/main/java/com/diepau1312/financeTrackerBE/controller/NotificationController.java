package com.diepau1312.financeTrackerBE.controller;

import com.diepau1312.financeTrackerBE.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notifService.getNotifications(page, size));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notifService.markAllRead();
        return ResponseEntity.ok().build();
    }
}