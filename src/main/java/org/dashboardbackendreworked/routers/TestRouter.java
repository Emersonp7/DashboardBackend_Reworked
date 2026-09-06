package org.dashboardbackendreworked.routers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestRouter {

    @GetMapping("/health")
    public String getServiceHealth() {
        return "Health: Ok";
    }

    @GetMapping("/ping")
    public String getPing() {
        return "Pong";
    }
    // TODO: Refactor GoogleCalendarService and NewsService before fully implementing endpoints.
}
