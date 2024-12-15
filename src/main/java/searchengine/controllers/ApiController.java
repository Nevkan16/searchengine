package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.ApiResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SiteService siteService;
    private final DummyDataService dummyDataService;
    private final DataService dataService;
    private final TestService testService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<ApiResponse> startIndexing() {
        if (siteService.isIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Индексация уже запущена"));
        }
        siteService.processSites();  // Теперь этот метод выполняется асинхронно
        return ResponseEntity.ok(new ApiResponse(true, null));  // Ответ возвращается сразу
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<ApiResponse> stopIndexing() {
        if (!siteService.isIndexing()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Индексация не запущена"));
        }
        siteService.stopProcessing();
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
            testService.deleteSiteData("https://www.lenta.ru");
            return ResponseEntity.ok(new ApiResponse(true, "Данные сайта успешно удалены"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, "Ошибка при удалении фиктивных данных"));
        }
    }
}
