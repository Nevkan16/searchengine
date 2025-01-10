package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.constants.ErrorMessages;
import searchengine.dto.ApiResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.*;
import searchengine.utils.EntityTableUtil;
import searchengine.utils.TestMethod;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteCRUDService siteCRUDService;
    private final IndexingServiceImpl indexingService;
    private final SiteCRUDService siteDataService;
    private final SearchService searchService;
    private final SiteDataExecutor siteDataExecutor;
    private final EntityTableUtil entityTableService;
    private final ApiResponse goodResponse = new ApiResponse(true, null);

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        if (!indexingService.startIndexing()) {
            ApiResponse response = new ApiResponse(false, ErrorMessages.INDEXING_ALREADY_RUNNING);
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(goodResponse);  // Ответ возвращается сразу
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!indexingService.stopIndexing()) {
            ApiResponse response = new ApiResponse(false, ErrorMessages.INDEXING_NOT_RUNNING);
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(goodResponse);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<ApiResponse> indexPage(@RequestParam String url) {
        if (!indexingService.indexPage(url)) {
            ApiResponse response = new ApiResponse(false, ErrorMessages.PAGE_OUTSIDE_CONFIGURED_SITES);
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(goodResponse);
    }

    // Удаление данных по URL сайта
    @GetMapping("/deleteSiteData")
    public ResponseEntity<ApiResponse> deleteSiteData() {
        try {
            siteCRUDService.deleteAllSites();
            siteDataService.isDatabaseEmpty();
            return ResponseEntity.ok(new ApiResponse(true, "Данные сайта успешно удалены"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(
                    false, "Ошибка при удалении фиктивных данных"));
        }
    }

    @GetMapping("/testService")
    public ResponseEntity<ApiResponse> someRequest() {
        try {
            siteDataExecutor.refreshAllSitesData();
            return ResponseEntity.ok(new ApiResponse(true, "Метод успешно выполнен"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Метод не выполнен"));
        }
    }

    @GetMapping("/resetInc")
    public ResponseEntity<ApiResponse> resetIncrement() {
        try {
            entityTableService.resetAutoIncrementForAllTables();
            return ResponseEntity.ok(new ApiResponse(true, "Метод успешно выполнен"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Метод не выполнен"));
        }
    }

    @GetMapping("/example")
    public ResponseEntity<ApiResponse> example(@RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.info(String.valueOf(limit));
       return ResponseEntity.ok(goodResponse);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "site", required = false) String site,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        if (query == null || query.isBlank()) {
            SearchResponse response = new SearchResponse
                    (false, null, null, null, null, ErrorMessages.EMPTY_QUERY);
            return ResponseEntity.badRequest().body(response);
        }

        if (site != null && site.isBlank()) {
            site = null;
        }

        SearchResponse searchResponse = searchService.search(query, site, offset, limit);

        return ResponseEntity.ok(searchResponse);
    }
}
