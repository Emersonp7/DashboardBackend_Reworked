package org.dashboardbackendreworked.models.LocationModels;

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
    private String Address;
    private String City;
    private String Street;
    private float Long;
    private float Lat;

    @Override
    public String toString() {
        return this.Address;
    }
}
