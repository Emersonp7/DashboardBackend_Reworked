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
public class PlaybackStatusModel {
    private String PlaybackStatus;
    private String PlaybackAction;

    @Override
    public String toString() {
        return "Status: " + this.PlaybackStatus + " - Playback Action: " + this.PlaybackAction;
    }
}
