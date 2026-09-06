package org.dashboardbackendreworked.service.SpotifyServices;

import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.Date;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;

import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import org.dashboardbackendreworked.models.SpotifyModels.SpotifyCredentialsModel;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@Component
public class SpotifyCredentialStore {
    @Value("${spotify.tokenFile:tokens/spotify-credentials.json}")
    private String filePath;

    final private static ObjectMapper mapper = new ObjectMapper();
    
    @PostConstruct
    private void postConstruct() {
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    private JsonNode convertToJson(SpotifyCredentialsModel spotifyCredentialsModel) throws Exception {
        return mapper.valueToTree(spotifyCredentialsModel);
    }
   
    private SpotifyCredentialsModel convertFromJson(JsonNode jsonModel) throws Exception {
        return mapper.treeToValue(
                jsonModel, SpotifyCredentialsModel.class
            );
    }
    
    public SpotifyCredentialsModel getSpotifyCredentialsModel() throws Exception {
        File file = new File(filePath);
        if ( !file.exists() || file.length() <= 0 ) {
            return SpotifyCredentialsModel.builder().build();
        }

        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line = reader.readLine(); 
        reader.close();
        SpotifyCredentialsModel credentialsModel = convertFromJson(mapper.readTree(line));

        return credentialsModel;
    }

    public SpotifyCredentialsModel saveAccessInformation(AuthorizationCodeCredentials credentials, String refreshToken) throws Exception {
        SpotifyCredentialsModel spotifyCredentialsModel = SpotifyCredentialsModel.builder()
            .accessToken(credentials.getAccessToken())
            .refreshToken(credentials.getRefreshToken() != null ? credentials.getRefreshToken() : refreshToken)
            .tokenType(credentials.getTokenType())
            .expiresIn(credentials.getExpiresIn())
            .scope(credentials.getScope())
            .createdAt(new Date().getTime())
        .build();

        JsonNode jsonModel = convertToJson(spotifyCredentialsModel);
        FileWriter writer = new FileWriter(filePath);
        writer.write(jsonModel.toString());
        writer.close();
        
        return spotifyCredentialsModel;
    }
    
}
