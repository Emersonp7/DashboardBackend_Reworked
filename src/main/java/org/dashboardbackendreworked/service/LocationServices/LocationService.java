package org.dashboardbackendreworked.service.LocationServices;

import java.nio.file.Files;
import java.nio.file.Path;

import org.dashboardbackendreworked.models.LocationModels.LocationModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LocationService {
    final private Path locationPath;
    final private ObjectMapper mapper = new ObjectMapper();

    public LocationService(@Value("${location.storage.path}") String locationPath)  { this.locationPath = Path.of(locationPath); }

    public void saveLastLocation(LocationModel locationModel) throws Exception {
        Files.createDirectories(this.locationPath.getParent());
        this.mapper.writeValue(this.locationPath.toFile(), locationModel);
    }

    public LocationModel getLastLocation() throws Exception {
        if (!Files.exists(this.locationPath)) return null;
        return this.mapper.readValue(this.locationPath.toFile(), LocationModel.class);
    }
}
