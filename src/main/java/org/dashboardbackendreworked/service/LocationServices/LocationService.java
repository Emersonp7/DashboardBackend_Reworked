package org.dashboardbackendreworked.service.LocationServices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dashboardbackendreworked.models.LocationModels.LocationModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class LocationService {

    public static class LocationLink {
        public LocationModel location;
        public LocationLink nextLocation;

        public LocationLink(LocationModel loc) {this.location = loc;}
        @Override
        public String toString() {
            return "-> " + this.location.toString() + "\n\t-> Next: " + this.nextLocation;
        }
    }

    final private Path locationPath;
    final private Path locationHistoryPath;
    final private ObjectMapper mapper = new ObjectMapper();

    public LocationService(
        @Value("${location.storage.path}") String locationPath, 
        @Value("${location.history.path}") String locationHistoryPath
    )  {
        this.locationPath = Path.of(locationPath); 
        this.locationHistoryPath = Path.of(locationHistoryPath);
    }
    
    @PostConstruct
    private void createParentDirectories() throws Exception {
        if (this.locationPath.getParent() != null ) 
            Files.createDirectories(this.locationPath.getParent());
        if (this.locationHistoryPath.getParent() != null)  
            Files.createDirectories(this.locationHistoryPath.getParent());
    }

    public void saveLastLocation(LocationModel locationModel) throws Exception {
        this.mapper.writeValue(this.locationPath.toFile(), locationModel);
        this.savetoLocationHistory(locationModel);
    }

    public LocationModel getLastLocation() throws Exception {
        if (!Files.exists(this.locationPath)) return null;
        return this.mapper.readValue(this.locationPath.toFile(), LocationModel.class);
    }

    public List<LocationModel> getLocationHistory() throws Exception {
        if (!Files.exists(this.locationHistoryPath)) return null;
        return mapper.readValue(this.locationHistoryPath.toFile(), new TypeReference<List<LocationModel>>() {});
    }

    private void savetoLocationHistory(LocationModel locationModel) throws Exception {
        List<LocationModel> locationHistorylList = this.getLocationHistory();
        if (locationHistorylList == null) locationHistorylList = new ArrayList<>();
        locationHistorylList.add(locationModel);
        this.mapper.writeValue(this.locationHistoryPath.toFile(), locationHistorylList);
    }

    public LocationLink getProbablePath(LocationModel start) throws Exception {
        List<LocationModel> locationHist = this.getLocationHistory();
        if (start == null || locationHist == null || locationHist.size() < 2) return null;

        String startAddress = start.getAddress();
        List<LocationLink> locationPaths = new ArrayList<>();

        int i = 0;
        while (i < locationHist.size()) {
            if (!this.sameAddress(locationHist.get(i), startAddress)) { i++; continue; }
            while (i + 1 < locationHist.size() && this.sameAddress(locationHist.get(i + 1), startAddress)) i++;

            LocationLink head = new LocationLink(locationHist.get(i));
            LocationLink tail = head;
            boolean arrived = false;
            int j = i + 1;
            while (j < locationHist.size() && !arrived) {
                LocationModel current = locationHist.get(j);
                tail.nextLocation = new LocationLink(current);
                tail = tail.nextLocation;
                arrived = j + 1 < locationHist.size()
                       && this.sameAddress(locationHist.get(j + 1), current.getAddress());
                j++;
            }

            if (arrived && head.nextLocation != null) locationPaths.add(head);
            i = j;
        }

        return this.getMostCommonPath(locationPaths);
    }

    private boolean sameAddress(LocationModel location, String address) {
        return location != null && Objects.equals(location.getAddress(), address);
    }

    private LocationLink getMostCommonPath(List<LocationLink> paths) {
        Map<String, Integer> pathCounts = new HashMap<>();
        Map<String, LocationLink> firstSeenPath = new HashMap<>();
        String bestKey = null;

        for (LocationLink path : paths) {
            StringBuilder key = new StringBuilder();
            for (LocationLink link = path; link != null; link = link.nextLocation) {
                key.append(link.location.getAddress()).append("->");
            }
            String pathKey = key.toString();
            pathCounts.merge(pathKey, 1, Integer::sum);
            firstSeenPath.putIfAbsent(pathKey, path);
            if (bestKey == null || pathCounts.get(pathKey) > pathCounts.get(bestKey)) bestKey = pathKey;
        }

        return bestKey == null ? null : firstSeenPath.get(bestKey);
    }
}
