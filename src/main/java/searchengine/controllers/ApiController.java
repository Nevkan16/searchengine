package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.PageService;
import searchengine.services.SiteService;
import searchengine.services.StatisticsService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteService siteService;
    private final PageService pageService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        if (siteService.isIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Индексация уже запущена"));
        }
        pageService.cleanData();
        siteService.indexAllSites();
        return ResponseEntity.ok(new ApiResponse(true, null));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!siteService.isIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Индексация не запущена"));
        }
        siteService.stopIndexing();
        return ResponseEntity.ok(new ApiResponse(true, null));
    }
}
