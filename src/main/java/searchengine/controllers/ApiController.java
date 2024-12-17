package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.*;
import searchengine.utils.HtmlLoader;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteService siteService;
    private final DummyDataService dummyDataService;
    private final DataService dataService;
    private final TestService testService;
    private final HtmlLoader htmlLoader;
    private final SiteDataExecutor siteDataExecutor;
    private final IndexingServiceImpl indexingService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        if (indexingService.startIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Индексация уже запущена"));
        }

        return ResponseEntity.ok(new ApiResponse(true, null));  // Ответ возвращается сразу
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!indexingService.stopIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Индексация не запущена"));
        }
        return ResponseEntity.ok(new ApiResponse(true, null));
    }

    // Добавление фиктивных данных
    @GetMapping("/addDummyData")
    public ResponseEntity<ApiResponse> addDummyData() {
        try {
            dummyDataService.addDummyData();
            return ResponseEntity.ok(new ApiResponse(true, "Фиктивные данные успешно добавлены"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Ошибка при добавлении фиктивных данных"));
        }
    }

    // Удаление данных по URL сайта
    @GetMapping("/deleteSiteData")
    public ResponseEntity<ApiResponse> deleteSiteData() {
        try {
            dataService.deleteSiteData();
            return ResponseEntity.ok(new ApiResponse(true, "Данные сайта успешно удалены"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Ошибка при удалении фиктивных данных"));
        }
    }
    @GetMapping("/testService")
    public ResponseEntity<ApiResponse> someRequest() {
        try {
            indexingService.startIndexing();
            return ResponseEntity.ok(new ApiResponse(true, "Метод успешно выполнен"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Метод не выполнен"));
        }
    }
}
