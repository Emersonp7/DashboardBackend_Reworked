package org.dashboardbackendreworked.service.LocationServices;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

//    public LocationLink getProbablePath(LocationModel location) throws Exception {
    public LocationLink getProbablePath() throws Exception {
        
        List<LocationLink> LocationPaths = new ArrayList<LocationLink>();
        List<LocationModel> locationHist = this.getLocationHistory();

        LocationModel location = locationHist.get(new Random().nextInt(0, locationHist.size()));

        
        // Stop when Same location appears twice or more time in a row.
        // Ex: Location1.address == Location2.address and Location1.timestamp + 5 minutes == Location2.timestamp
        // When stopped, if another Location1 appears again further down the list, follow same pattern.
        // In list? [LocationLink1, LocationLink2, LocationLink3] Where LocationLink1, LocationLink2, LocationLink3 all start with the same Location

        int left = 0;
        int right = 1;
        LocationLink locationLink = new LocationLink(null);
        LocationLink head = locationLink;
        while (left < right && Math.max(left, right) < locationHist.size()) {

            if (locationHist.get(left).getAddress().equals(location.getAddress())) {
                if ( left < right && locationHist.get(left+1).getAddress().equals(location.getAddress())) {
                    left++;
                }
                locationLink = new LocationLink(locationHist.get(left));
                head = locationLink;
            }

            if (locationHist.get(right).getAddress().equals(locationHist.get(right-1).getAddress())) {
                LocationPaths.add(locationLink);
                locationLink = new LocationLink(null);
                head = locationLink;
                left = right;
            }

            head.nextLocation = new LocationLink(locationHist.get(right));
            head = head.nextLocation;

            right++;
            left++;
        }

        for (LocationLink locLink: LocationPaths) {
            System.out.println(locLink);
        }

        return null;
    }
}
