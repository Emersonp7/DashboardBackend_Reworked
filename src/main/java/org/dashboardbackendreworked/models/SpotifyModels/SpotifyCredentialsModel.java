package org.dashboardbackendreworked.models.SpotifyModels;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
public class SpotifyCredentialsModel {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private String scope;
    private int expiresIn;
    private long createdAt;

    @JsonIgnore
    public boolean isExpired() {
        final long now = new Date().getTime();
        return Math.ceil((now - this.createdAt) / 1000.0) > this.expiresIn;
    }

    @JsonIgnore
    public boolean isValid() {
        return 
            this.accessToken != null &&
            this.refreshToken != null &&
            !this.accessToken.isEmpty() &&
            !this.refreshToken.isEmpty();

    }
}
