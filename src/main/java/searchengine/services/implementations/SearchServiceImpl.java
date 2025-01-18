package searchengine.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.search.Pagination;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.interfaces.SearchService;
import searchengine.utils.QueryUtil;
import searchengine.utils.SnippetGeneratorUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private String currentQuery;
    private int currentOffset;
    @Value("${search-results.showing-limit}")
    private int limit;
    private Set<PageEntity> currentMatchingPages;
    private Map<PageEntity, Float> currentPageRelevanceMap;
    private Set<String> uniqueLemmas;
    private final Map<String, SearchResult> snippetCache = new ConcurrentHashMap<>();
    private float absoluteRelevance;
    private float maxRelevance;
    private SiteEntity currentSiteEntity;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final SnippetGeneratorUtil snippetGeneratorUtil;
    private final QueryUtil queryUtil;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        long startTime = System.nanoTime();
        try {
            this.currentQuery = query;
            this.currentOffset = offset;

            log.info("Starting search with query: '{}', site: '{}', offset: {}, limit: {}",
                    query, site, offset, limit);

            Set<String> uniqueLemmas = queryUtil.extractLemmas(currentQuery);
            if (uniqueLemmas.isEmpty()) {
                return createEmptyResponse("No valid lemmas found");
            }

            this.currentSiteEntity = siteRepository.findByUrl(site).orElse(null);
            if (site != null && currentSiteEntity == null) {
                return createEmptyResponse("Site not found");
            }

            if (uniqueLemmas.isEmpty()) {
                return createEmptyResponse(null);
            }
            return processSearchResults(uniqueLemmas);
        } finally {
            long elapsedTime = System.nanoTime() - startTime;
            log.info("Search execution time: {} ms", elapsedTime / 1_000_000);
        }
    }

    // Создает пустой ответ с сообщением.
    private SearchResponse createEmptyResponse(String message) {
        log.info("Returning empty search result: {}", message);
        return new SearchResponse(true, 0, Collections.emptyList(), 0, 0, message);
    }


    // Извлекает заголовок из контента.
    private String extractTitleFromContent(String content) {
        int titleStart = content.indexOf("<title>");
        int titleEnd = content.indexOf("</title>");
        if (titleStart != -1 && titleEnd != -1) {
            return content.substring(titleStart + 7, titleEnd).trim();
        }
        return "No title";
    }

    public void clearSnippetCache() {
        synchronized (snippetCache) {
            int MAX_SNIPPET_CACHE_SIZE = 500;
            if (snippetCache.size() > MAX_SNIPPET_CACHE_SIZE) {
                snippetCache.clear();
                log.info("Snippet cache has been cleared");
            }
        }
    }

    // Преобразует страницу в результат поиска.
    private SearchResult mapToSearchResult(PageEntity page) {
        clearSnippetCache();
        String cacheKey = page.getContent().hashCode() + "_" + uniqueLemmas.hashCode();

        if (snippetCache.containsKey(cacheKey)) {
            return snippetCache.get(cacheKey);
        }

        float relativeRelevance = absoluteRelevance / maxRelevance;
        String title = extractTitleFromContent(page.getContent());

        String snippet = snippetGeneratorUtil.generateSnippet(page.getContent(), currentQuery);

        log.info("Page '{}', Max relevance '{}',  Absolute relevance '{}', Relative relevance '{}'",
                page.getPath(), maxRelevance, absoluteRelevance, relativeRelevance);

        if (snippet.trim().isEmpty()) {
            return null;
        }

        String sizeFont = "<h3>%s</h3>";
        String formattedTitle = String.format(sizeFont, title);
        String formattedSnippet = String.format(sizeFont, snippet);

        SearchResult result = SearchResult.builder()
                .site(page.getSite().getUrl())
                .siteName(page.getSite().getName())
                .uri(page.getPath())
                .title(formattedTitle)
                .snippet(formattedSnippet)
                .relevance(relativeRelevance)
                .build();

        snippetCache.put(cacheKey, result);

        return result;
    }

    private SearchResponse buildSearchResponse(int totalResults, List<SearchResult> paginatedSnippets, Pagination pagination) {
        return new SearchResponse(
                true,
                totalResults,
                paginatedSnippets,
                pagination.getCurrentPage(),
                pagination.getTotalPages(),
                null
        );
    }

    private List<SearchResult> getAllUniqueSnippets() {
        Set<String> processedSnippets = new HashSet<>();

        return currentMatchingPages
                .stream()
                .sorted((p1, p2) -> Float.compare(
                        currentPageRelevanceMap.get(p2),
                        currentPageRelevanceMap.get(p1)))
                .map(page -> {
                    this.absoluteRelevance = currentPageRelevanceMap.get(page);
                    SearchResult searchResult = mapToSearchResult(page);

                    if (searchResult != null && !searchResult.getSnippet().isEmpty()
                            && processedSnippets.add(searchResult.getSnippet())) {
                        return searchResult;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private Pagination calculatePagination(int totalResults, int limit, int offset) {
        int totalPages = (int) Math.ceil((double) totalResults / limit);
        int currentPage = (offset / limit) + 1;

        return new Pagination(totalResults, totalPages, currentPage, limit, offset);
    }

    private List<SearchResult> getPaginatedSnippets(List<SearchResult> allSnippets, Pagination pagination) {
        return allSnippets.stream()
                .skip((long) (pagination.getCurrentPage() - 1) * pagination.getLimit())
                .limit(pagination.getLimit())
                .toList();
    }

    // Генерирует ответ для поиска.
    private SearchResponse generateSearchResponse(Set<String> uniqueLemmas) {
        this.uniqueLemmas = uniqueLemmas;
        this.maxRelevance = Collections.max(currentPageRelevanceMap.values());

        List<SearchResult> allSnippets = getAllUniqueSnippets();

        Pagination pagination = calculatePagination(allSnippets.size(), limit, currentOffset);

        List<SearchResult> paginatedSnippets = getPaginatedSnippets(allSnippets, pagination);

        return buildSearchResponse(allSnippets.size(), paginatedSnippets, pagination);
    }

    // Получает страницы для конкретной леммы.
    private Set<PageEntity> findPagesForLemma(Set<String> lemmas) {
        log.info("Finding pages for lemmas: {} and siteEntity: {}",
                lemmas, currentSiteEntity == null ? "All sites" : currentSiteEntity.getUrl());

        return queryUtil.findMatchingPages(currentQuery, currentSiteEntity);
    }

    // Получает леммы для страницы.
    private Set<LemmaEntity> getLemmasForPage(PageEntity page) {
        return indexRepository.findByPage(page).stream()
                .map(IndexEntity::getLemma)
                .collect(Collectors.toSet());
    }

    // Получает рейтинг для леммы на странице.
    private float getRankForLemmaAndPage(PageEntity page, LemmaEntity lemma) {
        return indexRepository.findRankByPageAndLemma(page, lemma).orElse(0f);
    }

    // Вычисляет абсолютную релевантность страниц.
    private Map<PageEntity, Float> calculateAbsoluteRelevance() {
        Map<PageEntity, Float> pageRelevanceMap = new ConcurrentHashMap<>();
        currentMatchingPages.parallelStream().forEach(page -> {
            float totalRank = getLemmasForPage(page).stream()
                    .map(lemmaEntity -> getRankForLemmaAndPage(page, lemmaEntity))
                    .reduce(0f, Float::sum);

            pageRelevanceMap.put(page, totalRank);
        });

        return pageRelevanceMap;
    }

    // Обрабатывает результаты поиска.
    private SearchResponse processSearchResults(Set<String> uniqueLemmas) {
        this.currentMatchingPages = findPagesForLemma(uniqueLemmas);
        log.info("Found matching pages: {}", currentMatchingPages.size());

        if (currentMatchingPages.isEmpty()) {
            return createEmptyResponse(null);
        }

        this.currentPageRelevanceMap = calculateAbsoluteRelevance();
        if (currentPageRelevanceMap.isEmpty()) {
            return createEmptyResponse("No relevance data found");
        }

        return generateSearchResponse(uniqueLemmas);
    }
}