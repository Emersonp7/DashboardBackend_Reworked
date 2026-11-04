package org.dashboardbackendreworked.utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpResponseMethods {
    private static ObjectMapper mapper = new ObjectMapper(); 

    public static <T>T getResponseObject( String endpoint, TypeReference<T> valueType ) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
        String response = getHttpResponseBody(endpoint);
        return mapper.readValue(response, valueType);
    }

    public static <T>T getResponseObject( String endpoint, Class<T> valueType ) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
        String response = getHttpResponseBody(endpoint);
        return mapper.readValue(response, valueType);
    }

    public static String getHttpResponseBody(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body().toString();
    } 

    public static String getSpecificField(JsonNode node, String field) {
        if (field.equals("category")) {
            return node.path("item").path("unibrow").path("text").asText();
        }
        if (field.equals("publishedAt")) {
            return node.path("item").path("date").path(field).asText();
        }
        return node.path("item").path(field).path("primary").asText();
    }

    
    public static JsonNode getLastScriptContent(String responseBody) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
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
        return mapper.readTree(content);
    }

}
