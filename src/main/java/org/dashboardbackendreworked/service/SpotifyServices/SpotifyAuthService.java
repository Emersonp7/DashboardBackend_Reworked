package org.dashboardbackendreworked.service.SpotifyServices;

import java.net.URI;
import java.util.Scanner;
import jakarta.annotation.PostConstruct;

import org.dashboardbackendreworked.models.SpotifyModels.SpotifyCredentialsModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.enums.AuthorizationScope;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;


@Service
public class SpotifyAuthService {
    final private AuthorizationScope[] authorizationScopes = {
        AuthorizationScope.USER_READ_CURRENTLY_PLAYING, 
        AuthorizationScope.USER_READ_PLAYBACK_STATE,
        AuthorizationScope.USER_MODIFY_PLAYBACK_STATE
    };

    private SpotifyCredentialStore spotifyCredentialStore;
    private SpotifyCredentialsModel spotifyCredentialsModel;
    private String CLIENTSECRET;
    private String CLIENTID;
    private String REDIRECTURI;

    private SpotifyApi spotifyApi;

    public SpotifyAuthService (
            SpotifyCredentialStore spotifyCredentialStore,
            @Value("${spotify.redirectURI}") String redirectURI,
            @Value("${spotify.clientID}") String clientId,
            @Value("${spotify.clientSecret}") String clientSecret
        ) throws Exception {

        this.spotifyCredentialStore = spotifyCredentialStore;
        this.CLIENTID = clientId;
        this.CLIENTSECRET = clientSecret;
        this.REDIRECTURI = redirectURI;

        URI redirectUri = SpotifyHttpManager.makeUri(this.REDIRECTURI);
        spotifyApi = new SpotifyApi.Builder()
            .setClientId(CLIENTID)
            .setClientSecret(CLIENTSECRET)
            .setRedirectUri(redirectUri)
            .build();

        this.spotifyCredentialsModel = spotifyCredentialStore.getSpotifyCredentialsModel();
        if (this.spotifyCredentialsModel.isValid()) {
            this.setCredentialTokens();
        }
    }
    
    public String getAuthorizationUrl() {
        return spotifyApi.authorizationCodeUri()
            .scope(this.authorizationScopes)
            .build()
            .execute()
            .toString();
    }

    private void setCredentialTokens() {
        spotifyApi.setAccessToken(spotifyCredentialsModel.getAccessToken()); 
        spotifyApi.setRefreshToken(spotifyCredentialsModel.getRefreshToken());
    }

    public synchronized SpotifyApi setAuthentication(String redirectCode) throws Exception {
        if (!this.spotifyCredentialsModel.isValid()) {
            if (redirectCode == null) {
                throw new IllegalStateException("Not authorized yet — open " + getAuthorizationUrl());
            }
            AuthorizationCodeCredentials authorizationCodeCredentials = this.spotifyApi.authorizationCode(redirectCode).build().execute();
            this.spotifyCredentialsModel = this.spotifyCredentialStore.saveAccessInformation(authorizationCodeCredentials, null);
        }
        
        if  (!this.spotifyCredentialsModel.isExpired()) {
            AuthorizationCodeCredentials authorizationCodeCredentials = this.spotifyApi.authorizationCodeRefresh(
                this.CLIENTID, this.CLIENTSECRET, this.spotifyCredentialsModel.getRefreshToken()
            ).build().execute();
            this.spotifyCredentialsModel = this.spotifyCredentialStore.saveAccessInformation(
                authorizationCodeCredentials, this.spotifyCredentialsModel.getRefreshToken()
            );

        }
        this.setCredentialTokens();
        return this.spotifyApi;
    }

    public synchronized SpotifyApi getSpotifyApi() throws Exception {
        return this.setAuthentication(null);
    }

    private AuthorizationCodeCredentials authorizationCodeCredentials;
    private AuthorizationCodeUriRequest authorizationCodeUriRequest;
    private AuthorizationCodeRequest authorizationCodeRequest;

    public SpotifyApi cliSetAuthentication() throws Exception {
        SpotifyCredentialsModel spotifyCredentialsModel = spotifyCredentialStore.getSpotifyCredentialsModel();

        if (!spotifyCredentialsModel.isValid()) {
            System.out.println("No Valid Credentials Available. Creating Now");
            URI responseURI = authorizationCodeUriRequest.execute();
            System.out.println(responseURI.toString());

            Scanner scanner = new Scanner(System.in);
            System.out.print("Code: ");
            String redirectCode = scanner.nextLine();
            System.out.println("Redirect Code: " + redirectCode);
            scanner.close();

            authorizationCodeRequest = spotifyApi.authorizationCode(redirectCode).build();
            authorizationCodeCredentials = authorizationCodeRequest.execute();
            spotifyCredentialsModel = spotifyCredentialStore.saveAccessInformation(authorizationCodeCredentials, null);
        }
        
        if (spotifyCredentialsModel.isExpired()) {
            System.out.println("Credentials Expired. Refreshing Now");
            authorizationCodeCredentials = spotifyApi.authorizationCodeRefresh(
                    CLIENTID, CLIENTSECRET, spotifyCredentialsModel.getRefreshToken()
                ).build()
                .execute();

            spotifyCredentialsModel = spotifyCredentialStore.saveAccessInformation(authorizationCodeCredentials, spotifyCredentialsModel.getRefreshToken());
        }

        this.setCredentialTokens();
        return spotifyApi;
    }
    
}
