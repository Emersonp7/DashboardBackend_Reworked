package org.dashboardbackendreworked.routers.NewsRouters;

import java.util.List;

import org.dashboardbackendreworked.models.NewsModels.TelemundoModel;
import org.dashboardbackendreworked.service.NewsServices.TelemundoNewsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/news/telemundo")
public class TelemundoNewsRouter {
    final private TelemundoNewsService newsService;
    public TelemundoNewsRouter(TelemundoNewsService service) { this.newsService = service; }

    @GetMapping("/recent-news")
    public List<TelemundoModel> getRecentTelemundoNews( @RequestParam(required = false) Integer limit ) throws Exception {
        if (limit == null) {
            return this.newsService.getTelemundoNews(5);
        }
        return this.newsService.getTelemundoNews(limit);
    }
}
