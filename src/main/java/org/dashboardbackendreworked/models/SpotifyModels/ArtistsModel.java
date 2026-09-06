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
public class ArtistsModel {
    private String artistName;
    private String artistId;

    @Override
    public String toString() {
        return "Name: " + this.artistName + " --- Id: " + this.artistId;
    }
}
