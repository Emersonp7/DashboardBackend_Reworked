package org.dashboardbackendreworked.models.LocationModels;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LocationModel {
    private String address;
    private String city;
    private String street;
    @JsonProperty("long") private Float _long;
    @JsonProperty("lat") private Float _lat;
    private String timestamp;

    @Override
    public String toString() {
        return this.address;
    }
}
