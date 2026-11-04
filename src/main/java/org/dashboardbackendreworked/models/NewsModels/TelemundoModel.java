package org.dashboardbackendreworked.models.NewsModels;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelemundoModel {
    private String headline;
    private String description;
    private String category;
    private String publishedAt;
    private String url;

    @Override
    public String toString() {
        return "Headline: " + this.headline + "\tDescription: " + this.description;
    }
}
