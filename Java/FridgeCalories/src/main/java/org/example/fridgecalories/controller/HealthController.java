package org.example.fridgecalories.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A cheap liveness check for the uptime monitor that keeps the deployed service
 * from being suspended for inactivity.
 * <p>
 * It deliberately touches nothing — no database, no external API. The monitor
 * calls this every few minutes around the clock, so anything done here is done
 * thousands of times a month for no benefit. Confirming the process is up and
 * serving requests is the whole job.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
