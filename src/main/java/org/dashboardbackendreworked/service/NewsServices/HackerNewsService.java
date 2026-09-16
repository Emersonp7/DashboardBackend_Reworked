package org.dashboardbackendreworked.service.NewsServices;

import org.dashboardbackendreworked.models.NewsModels.HackerNewsItem;
import org.dashboardbackendreworked.models.NewsModels.HackerNewsModel;
import org.dashboardbackendreworked.utils.HttpResponseMethods;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class HackerNewsService {
    final static private String HACKERNEWS = "https://hacker-news.firebaseio.com/v0";

    public List<HackerNewsModel> getHackerNews(int maxStories) throws JsonMappingException, JsonProcessingException, IOException, InterruptedException {
        List<HackerNewsModel> hackernewsModelList = new ArrayList<HackerNewsModel>();

        List<Integer> ids = HttpResponseMethods.getResponseObject(HACKERNEWS + "/topstories.json", new TypeReference<List<Integer>>() {}).subList(0, maxStories);
        for (Integer id : ids) {
            HackerNewsItem item = HttpResponseMethods.getResponseObject(HACKERNEWS + "/item/" + id.toString() + ".json", HackerNewsItem.class);
            HackerNewsModel hackerNewsModel = HackerNewsModel.builder()
                .title(item.getTitle())
                .id(item.getId())
                .url(item.getUrl())
                .text(item.getText() != null ? item.getText() : "")
                .build()
                ;
            hackernewsModelList.add( hackerNewsModel );
        }
        return hackernewsModelList;
    }

}
