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

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingServiceImpl indexingService;
    private final SearchService searchService;
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

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "site", required = false) String site) {

        int currentLimit = 0;

        if (query == null || query.isBlank()) {
            SearchResponse response = new SearchResponse(
                    false, null, null, null, null, ErrorMessages.EMPTY_QUERY);
            return ResponseEntity.badRequest().body(response);
        }

        if (site != null && site.isBlank()) {
            site = null;
        }
        int currentOffset = 0;
        SearchResponse searchResponse = searchService.search(query, site, currentOffset, currentLimit);

        return ResponseEntity.ok(searchResponse);
    }
}
