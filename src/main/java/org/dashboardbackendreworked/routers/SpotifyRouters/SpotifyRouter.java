package org.dashboardbackendreworked.routers.SpotifyRouters;

import java.util.List;

import org.dashboardbackendreworked.models.SpotifyModels.TrackModel;
import org.dashboardbackendreworked.service.SpotifyServices.SpotifyAuthService;
import org.dashboardbackendreworked.service.SpotifyServices.SpotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spotify")
public class SpotifyRouter {
    final private SpotifyAuthService spotifyAuthService;
    final private SpotifyService spotifyService;

    public SpotifyRouter(SpotifyAuthService authService, SpotifyService spotifyService) {
        this.spotifyAuthService = authService;
        this.spotifyService = spotifyService;
    }

    @GetMapping("/health")
    public String getServiceHealth() {
        return "Health: Ok";
    }
    @GetMapping("/authorize")
    public String authorize() {
        return this.spotifyAuthService.getAuthorizationUrl();
    }

    @GetMapping("/callback")
    public String setSpotifyAPI(@RequestParam String code) throws Exception {
        this.spotifyAuthService.setAuthentication(code);
        return "Spotify Authorized";
    }

    @PostMapping("/skip")
    public void skipTrack() throws Exception {
        spotifyService.skipToNextTrack();
    }

    @GetMapping("/current-track")
    public TrackModel getCurrentTrack() throws Exception {
        return this.spotifyService.getCurrentTrack();
    }

    @GetMapping("/queue")
    public List<TrackModel> getQueue() throws Exception {
        return this.spotifyService.getQueuedTracks();
    }
}
