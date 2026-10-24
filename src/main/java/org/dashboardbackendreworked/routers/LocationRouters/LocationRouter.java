package org.dashboardbackendreworked.routers.LocationRouters;

import java.util.Map;

import org.dashboardbackendreworked.models.LocationModels.LocationModel;
import org.dashboardbackendreworked.service.LocationServices.LocationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location")
public class LocationRouter {
    final private LocationService locationService;
    public LocationRouter(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/health")
    public Map<String, String> getServiceHealth() {
        return Map.of(
            "status", "ok", "service", "spotify"
        );
    }
    
    @PostMapping("/set")
    public void setLocation(@RequestBody LocationModel locationModel) throws Exception {
        this.locationService.saveLastLocation(locationModel);
    }

    @GetMapping("/get")
    public LocationModel getLocation() throws Exception {
        return this.locationService.getLastLocation();
    }
}
