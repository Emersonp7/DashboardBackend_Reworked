package org.dashboardbackendreworked.models.NewsModels;

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
public class HackerNewsModel {
    private String title;
    private int id;
    private String url;
    private String text;

    @Override
    public String toString() {
        return "Title: " + this.title + "\turl: " + this.url;
    }
}

