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
import searchengine.utils.EntityTableService;
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
    private final TestMethod testMethod;
    private final SearchService searchService;
    private final SiteDataExecutor siteDataExecutor;
    private final EntityTableService entityTableService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        if (!indexingService.startIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, ErrorMessages.INDEXING_ALREADY_RUNNING));
        }

        return ResponseEntity.ok(new ApiResponse(true, null));  // Ответ возвращается сразу
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!indexingService.stopIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(
                    false, ErrorMessages.INDEXING_NOT_RUNNING));
        }
        return ResponseEntity.ok(new ApiResponse(true, null));
    }
    @PostMapping("/indexPage")
    public ResponseEntity<ApiResponse> indexPage(@RequestParam String url) {
        if (indexingService.indexPage(url)) {
            return ResponseEntity.badRequest().body(new ApiResponse(
                    false, ErrorMessages.PAGE_OUTSIDE_CONFIGURED_SITES));
        }
        return ResponseEntity.ok(new ApiResponse(true, null));
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
            siteDataService.resetIncrement();
            return ResponseEntity.ok(new ApiResponse(true, "Метод успешно выполнен"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Метод не выполнен"));
        }
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "site", required = false) String site,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        log.info("Search request received: query='{}', site='{}', offset={}, limit={}", query, site, offset, limit);

        // Проверяем, что запрос не пустой
        if (query == null || query.isBlank()) {
            return new SearchResponse(false, null, null, "Задан пустой поисковый запрос");
        }

        // Если выбрано "All sites", передаем null как параметр для site
        if (site != null && site.isBlank()) {
            site = null;
        }

        // Вызываем сервис поиска
        return searchService.search(query, site, offset, limit);
    }
}
