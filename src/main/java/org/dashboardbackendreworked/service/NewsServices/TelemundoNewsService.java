package org.dashboardbackendreworked.service.NewsServices;

import org.dashboardbackendreworked.models.NewsModels.TelemundoModel;
import org.dashboardbackendreworked.utils.HttpResponseMethods;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TelemundoNewsService {
    final static private String TELEMUNDOAPIENDPOINT = "https://www.telemundo.com/noticias";

    public List<TelemundoModel> getTelemundoNews(int maxStories) throws IOException, InterruptedException {
        List<TelemundoModel> newsData = new ArrayList<TelemundoModel>();
        String responseBody = HttpResponseMethods.getHttpResponseBody(TELEMUNDOAPIENDPOINT);
        JsonNode root = HttpResponseMethods.getLastScriptContent(responseBody);
        JsonNode items = root.path("props").path("initialState").path("front").path("curation").path("layouts").get(3).path("packages").get(0).path("items");

        for (JsonNode item : items) {
            String headline = HttpResponseMethods.getSpecificField(item, "headline");
            String description = HttpResponseMethods.getSpecificField(item, "description");
            String category = HttpResponseMethods.getSpecificField(item, "category");
            String publishedAt = HttpResponseMethods.getSpecificField(item, "publishedAt");
            String url = HttpResponseMethods.getSpecificField(item, "url");
            
            TelemundoModel data = TelemundoModel
                    .builder()
                    .headline(headline)
                    .description(description)
                    .category(category)
                    .publishedAt(publishedAt)
                    .url(url)
                    .build();
            newsData.add( data );

            }
        if (maxStories > newsData.size()) {
            return newsData;
        }

        return newsData.subList(0, maxStories); 
    }

}
