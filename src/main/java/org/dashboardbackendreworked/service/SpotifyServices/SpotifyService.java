package org.dashboardbackendreworked.service.SpotifyServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.ParseException;
import org.dashboardbackendreworked.models.SpotifyModels.DeviceModel;
import org.dashboardbackendreworked.models.SpotifyModels.ArtistsModel;
import org.dashboardbackendreworked.models.SpotifyModels.TrackModel;
import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlaying;
import se.michaelthelin.spotify.model_objects.miscellaneous.Device;
import se.michaelthelin.spotify.model_objects.special.PlaybackQueue;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.player.GetTheUsersQueueRequest;
import se.michaelthelin.spotify.requests.data.player.GetUsersAvailableDevicesRequest;
import se.michaelthelin.spotify.requests.data.player.GetUsersCurrentlyPlayingTrackRequest;
import se.michaelthelin.spotify.requests.data.player.PauseUsersPlaybackRequest;
import se.michaelthelin.spotify.requests.data.player.StartResumeUsersPlaybackRequest;

@Service
public class SpotifyService {
    final private SpotifyAuthService spotifyAuthService;
    final private String DEFAULTDEVICENAME = "Em v2";

    public SpotifyService(SpotifyAuthService authService) { this.spotifyAuthService = authService; }

    public DeviceModel getActiveDevice() throws Exception, IOException, SpotifyWebApiException, ParseException {
        for (DeviceModel device: this.getAvailableDevices()) {
            if (device.getIsActive()) {
                return device;
            }
        }
        return DeviceModel.builder().build();
    }

    private DeviceModel getSpecificDeviceByName(String deviceName) throws Exception, IOException, SpotifyWebApiException, ParseException {
        List<DeviceModel> devices = this.getAvailableDevices();
        for (DeviceModel device: devices) {
            if (device.getDeviceName().equals(deviceName)) {
                return device;
            }
        }
        return DeviceModel.builder().build();
    }

    public List<DeviceModel> getAvailableDevices() throws Exception, IOException, SpotifyWebApiException, ParseException {
        List<DeviceModel> deviceList = new ArrayList<DeviceModel>();
        SpotifyApi api = this.spotifyAuthService.getSpotifyApi();
        GetUsersAvailableDevicesRequest devicesRequest = api.getUsersAvailableDevices().build();
        Device[] devices = devicesRequest.execute();
        for (Device device: devices) {
            DeviceModel deviceModel = DeviceModel.builder()
                .deviceName(device.getName())
                .deviceId(device.getId())
                .isActive(device.getIs_active())
                .isPrivateSession(device.getIs_private_session())
                .supportsVolume(device.getSupports_volume())
                .type(device.getType())
                .volumePercent(device.getVolume_percent())
                .build();
            deviceList.add(deviceModel);
        }

        return deviceList;
    }

    private TrackModel createTrackModel(IPlaylistItem item) {
        if (!(item instanceof Track)) {
            return TrackModel.builder().build();
        }

        List<ArtistsModel> artistsList = new ArrayList<ArtistsModel>();
        List<String> imageUrlList = new ArrayList<String>();
        Track itemTrack = (Track) item;
        for (Image imageUrl: itemTrack.getAlbum().getImages()) {
            imageUrlList.add(imageUrl.getUrl());
        }

        for (ArtistSimplified artist: itemTrack.getArtists()) {
            ArtistsModel artistsModel = ArtistsModel.builder()
                .artistName(artist.getName())
                .artistId(artist.getId())
                .build();
            artistsList.add(artistsModel);
        }
        
        TrackModel track = TrackModel.builder()
            .trackName(itemTrack.getName())
            .trackId(itemTrack.getId())
            .durationMs(itemTrack.getDurationMs())
            .imageUrls(imageUrlList)
            .artists(artistsList)
            .build();
        
        return track;
    }

    public List<TrackModel> getQueuedTracks() throws Exception, IOException, SpotifyWebApiException, ParseException {
        List<TrackModel> queuedTracksModels = new ArrayList<TrackModel>();
        SpotifyApi api = this.spotifyAuthService.getSpotifyApi();
        GetTheUsersQueueRequest queueRequest = api.getTheUsersQueue().build();
        PlaybackQueue queue = queueRequest.execute();

        if (queue.getQueue().size() <= 0) {
            return new ArrayList<TrackModel>();
        }

        for (IPlaylistItem playlistItem: queue.getQueue()) {
            TrackModel track = this.createTrackModel(playlistItem);
            queuedTracksModels.add(track);
        }

        return queuedTracksModels;
    }

    public TrackModel getCurrentTrack() throws Exception, IOException, SpotifyWebApiException, ParseException {
        SpotifyApi api = this.spotifyAuthService.getSpotifyApi();
        GetUsersCurrentlyPlayingTrackRequest currentTrackRequest = api.getUsersCurrentlyPlayingTrack().build();
        CurrentlyPlaying currentTrack = currentTrackRequest.execute();
        if (currentTrack == null || !currentTrack.getIs_playing()) {
            return TrackModel.builder().build();
        }

        return this.createTrackModel(currentTrack.getItem());
    }

    public void skipToNextTrack() throws Exception, IOException, SpotifyWebApiException, ParseException {
        SpotifyApi api = this.spotifyAuthService.getSpotifyApi();
        api.skipUsersPlaybackToNextTrack().build().execute();
    }
    public void skipToPrevTrack() throws Exception, IOException, SpotifyWebApiException, ParseException {
        SpotifyApi api = this.spotifyAuthService.getSpotifyApi();
        api.skipUsersPlaybackToPreviousTrack().build().execute();
    }

    public boolean TogglePlayback() throws Exception, IOException, SpotifyWebApiException, ParseException {
        SpotifyApi api = this.spotifyAuthService.getSpotifyApi();
        PauseUsersPlaybackRequest pauseRequest = api.pauseUsersPlayback().build();
        StartResumeUsersPlaybackRequest playRequest;
        DeviceModel activeDevice = this.getActiveDevice();
        if (activeDevice.getDeviceName() == null) {
            playRequest = api.startResumeUsersPlayback()
                .device_id(
                        this.getSpecificDeviceByName(DEFAULTDEVICENAME)
                        .getDeviceId())
                .build();
        } else {
            playRequest = api.startResumeUsersPlayback().build();
        }
        
        CurrentlyPlaying currentTrack = api.getUsersCurrentlyPlayingTrack().build().execute();
        if (currentTrack != null && currentTrack.getIs_playing()) {
            pauseRequest.execute();
            return false;
        } else {
           playRequest.execute();
            return true;
        }
    }

}
