package org.dashboardbackendreworked.models.SpotifyModels;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceModel {
    private String deviceName;
    private String deviceId;
    private Boolean isActive;
    private Boolean isPrivateSession;
    private Boolean supportsVolume;
    private String type;
    private int volumePercent;

    @Override
    public String toString() {
        return "Device Name: " + this.deviceName + " - Device Id: " + this.deviceId;
    }
}
