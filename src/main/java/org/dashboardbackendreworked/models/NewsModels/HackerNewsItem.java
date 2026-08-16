
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
public class HackerNewsItem {
    String by;
    int descendants;
    int id;
    int[] kids;
    int score;
    int time;
    String title;
    String type;
    String url;
}
