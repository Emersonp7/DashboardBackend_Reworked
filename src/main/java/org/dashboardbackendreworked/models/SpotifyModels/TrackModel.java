package org.dashboardbackendreworked.models.SpotifyModels;

import java.util.List;

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
public class TrackModel {
    private String trackId;
    private String trackName;
    private List<String> imageUrls;
    private List<ArtistsModel> artists;
    private int durationMs;

    @Override
    public String toString() {
        return "Track: " + this.trackName + " --- Duration: " + this.durationMs;
    }

}

