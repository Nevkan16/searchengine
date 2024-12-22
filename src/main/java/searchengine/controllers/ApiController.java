package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.constants.ErrorMessages;
import searchengine.dto.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteCRUDService siteCRUDService;
    private final SiteDataExecutor siteDataExecutor;
    private final IndexingServiceImpl indexingService;
    private final SiteCRUDService siteDataService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        if (!indexingService.startIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, ErrorMessages.INDEXING_NOT_RUNNING));
        }

        return ResponseEntity.ok(new ApiResponse(true, null));  // Ответ возвращается сразу
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!indexingService.stopIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(
                    false, ErrorMessages.INDEXING_ALREADY_RUNNING));
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
}
