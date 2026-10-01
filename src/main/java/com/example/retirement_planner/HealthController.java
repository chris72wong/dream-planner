package com.example.retirement_planner;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight readiness request: no calculations or user data. */
@RestController
public class HealthController {
    @GetMapping("/api/health")
    public ResponseEntity<Void> health() {
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
