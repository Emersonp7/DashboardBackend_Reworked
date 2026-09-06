package org.dashboardbackendreworked.routers;

import org.dashboardbackendreworked.service.SpotifyServices.SpotifyAuthService;
import org.dashboardbackendreworked.service.SpotifyServices.SpotifyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import se.michaelthelin.spotify.SpotifyApi;

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
        System.out.println("REDIRECT CODE: " + code);
        this.spotifyAuthService.setAuthentication(code);
        return "Spotify Authorized";
    }
}
