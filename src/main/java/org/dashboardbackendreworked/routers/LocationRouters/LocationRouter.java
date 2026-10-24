package org.dashboardbackendreworked.routers.LocationRouters;


public class LocationRouter {
    @GetMapping("/health")
    public Map<String, String> getServiceHealth() {
        return Map.of(
            "status", "ok", "service", "spotify"
        );
    }
}
