package org.dashboardbackendreworked.service.NewsServices;
import org.dashboardbackendreworked.models.NewsModels.HackerNewsItem;
import org.dashboardbackendreworked.models.NewsModels.HackerNewsModel;
import org.dashboardbackendreworked.models.NewsModels.TelemundoModel;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Service;

@Service
public class NewsService {
    final static private String TELEMUNDOAPIENDPOINT = "https://www.telemundo.com/noticias";
    final static private String HACKERNEWS = "https://hacker-news.firebaseio.com/v0";
    private ObjectMapper mapper = new ObjectMapper(); 

    private <T>T getResponseObject( String endpoint, TypeReference<T> valueType ) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
        String response = this.getHttpResponseBody(endpoint);
        return  this.mapper.readValue(response, valueType);
    }

    private <T>T getResponseObject( String endpoint, Class<T> valueType ) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
        String response = this.getHttpResponseBody(endpoint);
        return  this.mapper.readValue(response, valueType);
    }

    private String getSpecificField(JsonNode node, String field) {
        if (field.equals("category")) {
            return node.path("item").path("unibrow").path("text").asText();
        }
        if (field.equals("publishedAt")) {
            return node.path("item").path("date").path(field).asText();
        }
        return node.path("item").path(field).path("primary").asText();
    }

    private String getHttpResponseBody(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body().toString();
    } 
    
    private JsonNode getLastScriptContent(String responseBody) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
        int openScriptTag = responseBody.indexOf("<script id=\"__NEXT_DATA__\"");
        if (openScriptTag == -1) {
            throw new IllegalStateException("No Tag Found");
        }

        int endScriptTag = responseBody.indexOf(">", openScriptTag);
        int scriptContent = endScriptTag + 1;
        int endScript = responseBody.indexOf("</script>", scriptContent);
        if (endScript == -1) {
            throw new IllegalStateException("No End Script Tag");
        }
        
        String content = responseBody.substring(scriptContent, endScript);
        return this.mapper.readTree(content);
    }

    public List<TelemundoModel> getTelemundoNews(int maxStories) throws IOException, InterruptedException {
        List<TelemundoModel> newsData = new ArrayList<TelemundoModel>();
        String responseBody = this.getHttpResponseBody(TELEMUNDOAPIENDPOINT);
        JsonNode root = this.getLastScriptContent(responseBody);
        JsonNode items = root.path("props").path("initialState").path("front").path("curation").path("layouts").get(3).path("packages").get(0).path("items");

        for (JsonNode item : items) {
            String headline = this.getSpecificField(item, "headline");
            String description = this.getSpecificField(item, "description");
            String category = this.getSpecificField(item, "category");
            String publishedAt = this.getSpecificField(item, "publishedAt");
            String url = this.getSpecificField(item, "url");
            
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
    
    public List<HackerNewsModel> getHackerNews(int maxStories) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
        List<HackerNewsModel> hackernewsModelList = new ArrayList<HackerNewsModel>();

        List<Integer> ids = this.getResponseObject(HACKERNEWS + "/topstories.json", new TypeReference<List<Integer>>() {}).subList(0, maxStories);
        for (Integer id : ids) {
            HackerNewsItem item = this.getResponseObject(HACKERNEWS + "/item/" + id.toString() + ".json", HackerNewsItem.class);
            HackerNewsModel hackerNewsModel = HackerNewsModel.builder()
                .title(item.getTitle())
                .id(item.getId())
                .url(item.getUrl())
                .build()
                ;
            hackernewsModelList.add( hackerNewsModel );
        }
        return hackernewsModelList;
    }

}
