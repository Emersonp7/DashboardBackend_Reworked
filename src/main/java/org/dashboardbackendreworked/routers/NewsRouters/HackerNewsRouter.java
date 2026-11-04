package org.dashboardbackendreworked.routers.NewsRouters;

import java.util.List;

import org.dashboardbackendreworked.models.NewsModels.HackerNewsModel;
import org.dashboardbackendreworked.service.NewsServices.HackerNewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news/hacker-news")
public class HackerNewsRouter {
    final private HackerNewsService newsService;
    public HackerNewsRouter(HackerNewsService service) { this.newsService = service; }
    
    @GetMapping("/recent-news")
    public List<HackerNewsModel> getRecentHackerNews( @RequestParam(required = false) Integer limit ) throws Exception {
        if (limit == null) {
            return this.newsService.getHackerNews(5);
        }
        return this.newsService.getHackerNews(limit);
    }
}
