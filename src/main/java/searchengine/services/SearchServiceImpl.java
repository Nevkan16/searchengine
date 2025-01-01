package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.Lemmatizer;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final double PERCENT_THRESHOLD = 0.5;
    private static final int SNIPPET_WINDOW = 30;
    private Set<String> lemmas;
    private Map<Integer, String> matches;
    private String[] words;
    private String currentQuery;
    private int currentOffset;
    private int currentLimit;
    private Set<PageEntity> currentMatchingPages;
    private Map<PageEntity, Float> currentPageRelevanceMap;
    private SiteEntity currentSiteEntity;
    private final Lemmatizer lemmatizer;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        long startTime = System.nanoTime();
        try {
            this.currentQuery = query;
            this.currentOffset = offset;
            this.currentLimit = limit;

            log.info("Starting search with query: '{}', site: '{}', offset: {}, limit: {}",
                    query, site, offset, limit);

            Set<String> uniqueLemmas = extractLemmas();
            if (uniqueLemmas.isEmpty()) {
                return createEmptyResponse("No valid lemmas found");
            }

            this.currentSiteEntity = validateSite(site);
            if (site != null && currentSiteEntity == null) {
                return createEmptyResponse("Site not found");
            }

            long totalPages = countPages();
            filterLemmas(uniqueLemmas, totalPages);

            if (uniqueLemmas.isEmpty()) {
                return createEmptyResponse(null);
            }
            return processSearchResults(uniqueLemmas);
        } finally {
            long elapsedTime = System.nanoTime() - startTime; // Рассчитываем время выполнения
            log.info("Search execution time: {} ms", elapsedTime / 1_000_000); // Переводим наносекунды в миллисекунды
        }
    }

    // Создает пустой ответ с сообщением.
    private SearchResponse createEmptyResponse(String message) {
        log.info("Returning empty search result: {}", message);
        return new SearchResponse(true, 0, Collections.emptyList(), message);
    }

    // Извлекает леммы из строки запроса.
    private Set<String> extractLemmas() {
        Set<String> uniqueLemmas = lemmatizer.extractLemmasFromQuery(currentQuery);
        log.info("Extracted Lemmas: {}", uniqueLemmas);
        return uniqueLemmas;
    }

    // Проверяет наличие сайта.
    private SiteEntity validateSite(String site) {
        if (site == null) {
            return null;
        }
        return siteRepository.findByUrl(site).orElse(null);
    }

    // Подсчитывает количество страниц для заданного сайта.
    private long countPages() {
        long totalPages =
                (currentSiteEntity == null) ? pageRepository.count() : pageRepository.countBySite(currentSiteEntity);
        log.info("Total pages for search: {}", totalPages);
        return totalPages;
    }

    // Получает леммы, которые нужно исключить.
    private Set<String> getExcludedLemmas(Set<String> lemmas, long totalPages) {
        Set<String> excludedLemmas = new HashSet<>();
        log.info("Filtering excluded lemmas. Total pages: {}", totalPages);

        List<LemmaEntity> lemmaEntities = lemmaRepository.findByLemmaInOrderByFrequencyAsc(new ArrayList<>(lemmas));
        for (LemmaEntity lemmaEntity : lemmaEntities) {
            long lemmaPageCount = lemmaEntity.getIndexes().stream()
                    .map(IndexEntity::getPage)
                    .distinct()
                    .count();

            double percentage = (double) lemmaPageCount / totalPages;

            if (percentage > PERCENT_THRESHOLD) {
                excludedLemmas.add(lemmaEntity.getLemma());
                log.info("Excluding lemma '{}' with frequency {} on {}% of total pages.",
                        lemmaEntity.getLemma(), lemmaPageCount, percentage * 100);
            }
        }
        log.info("Excluded lemmas: {}", excludedLemmas);
        return excludedLemmas;
    }

    // Фильтрует леммы в зависимости от общего количества страниц.
    private void filterLemmas(Set<String> uniqueLemmas, long totalPages) {
        Set<String> excludedLemmas = getExcludedLemmas(uniqueLemmas, totalPages);
        uniqueLemmas.removeAll(excludedLemmas);
        log.info("Remaining Lemmas after exclusion: {}", uniqueLemmas);
    }

    // Обновляет список страниц на основе лемм.
    private void updatePagesBasedOnLemma(Set<PageEntity> pages, Set<PageEntity> lemmaPages, LemmaEntity lemmaEntity) {
        if (pages.isEmpty()) {
            pages.addAll(lemmaPages);
        } else {
            log.info("Pages for lemma '{}': {}", lemmaEntity.getLemma(),
                    lemmaPages.stream().map(PageEntity::getPath).collect(Collectors.toList()));

            Set<PageEntity> intersection = new HashSet<>(pages);
            intersection.retainAll(lemmaPages);

            if (!intersection.isEmpty()) {
                pages.retainAll(lemmaPages);
                log.info("Pages after intersection: {}", pages.stream().map(PageEntity::getPath).collect(Collectors.toList()));
            } else {
                pages.addAll(lemmaPages);
                log.info("No intersection found. Added pages: {}",
                        lemmaPages.stream().map(PageEntity::getPath).collect(Collectors.toList()));
            }
        }

        if (lemmaPages.isEmpty()) {
            pages.clear();
        }
    }

    // Получает страницы для конкретной леммы.
    private Set<PageEntity> getPagesForLemma(LemmaEntity lemmaEntity) {
        Set<PageEntity> pages = new HashSet<>();
        log.info("Extracting pages for LemmaEntity with id: {}, lemma: '{}', frequency: {}",
                lemmaEntity.getId(), lemmaEntity.getLemma(), lemmaEntity.getFrequency());

        for (IndexEntity index : lemmaEntity.getIndexes()) {
            PageEntity page = index.getPage();
            log.info("Found page with id: {}, path: '{}' for lemma: '{}'",
                    page.getId(), page.getPath(), lemmaEntity.getLemma());
            pages.add(page);
        }
        log.info("Total pages found for LemmaEntity with id: {}: {}", lemmaEntity.getId(), pages.size());
        return pages;
    }

    // Фильтрует страницы по сайту.
    private Set<PageEntity> filterPagesBySite(Set<PageEntity> lemmaPages, SiteEntity siteEntity) {
        if (siteEntity == null) {
            return lemmaPages;
        }
        return lemmaPages.stream()
                .filter(pageEntity -> pageEntity.getSite().equals(siteEntity))
                .collect(Collectors.toSet());
    }


    // Находит страницы, соответствующие леммам.
    private Set<PageEntity> findMatchingPages(List<LemmaEntity> lemmaEntities, SiteEntity siteEntity) {
        Set<PageEntity> pages = new HashSet<>();
        for (LemmaEntity lemmaEntity : lemmaEntities) {
            Set<PageEntity> lemmaPages = getPagesForLemma(lemmaEntity);
            lemmaPages = filterPagesBySite(lemmaPages, siteEntity);

            updatePagesBasedOnLemma(pages, lemmaPages, lemmaEntity);
            if (pages.isEmpty() && lemmaPages.isEmpty()) {
                break;
            }
        }
        log.info("Found {} matching pages for lemmas.", pages.size());
        return pages;
    }

    // Находит совпадения лемм в тексте.
    private Map<Integer, String> findMatches(String content) {
        matches = new TreeMap<>();
        words = content.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            Set<String> wordsLemmas = lemmatizer.extractLemmasFromQuery(words[i]);
            if (!Collections.disjoint(lemmas, wordsLemmas)) {
                matches.put(i, words[i]);
            }
        }
        return matches;
    }

    // Вычисляет начальный индекс для сниппета.
    private int calculateStartIndex(int index) {
        return Math.max(0, index - SNIPPET_WINDOW / 2);
    }

    // Вычисляет конечный индекс для сниппета.
    private int calculateEndIndex(int index) {
        return Math.min(words.length, index + SNIPPET_WINDOW / 2);
    }

    // Проверяет, является ли слово релевантным для лемм.
    private boolean isWordRelevant(String word) {
        Set<String> wordLemmas = lemmatizer.extractLemmasFromQuery(word);
        return !Collections.disjoint(lemmas, wordLemmas);
    }

    // Добавляет слова в сниппет.
    private int appendWordsToSnippet(StringBuilder builder, int start, int end, int snippetLength) {
        for (int i = start; i < end && snippetLength < SNIPPET_WINDOW; i++) {
            String word = words[i];
            if (isWordRelevant(word)) {
                builder.append("<b>").append(word).append("</b> ");
            } else {
                builder.append(word).append(" ");
            }
            snippetLength++;
        }
        return snippetLength;
    }

    // Строит сниппет на основе найденных совпадений с учетом дополнительного условия.
    private String generateSnippet(boolean checkLemmas) {
        StringBuilder builder = new StringBuilder();
        int snippetLength = 0;

        for (Map.Entry<Integer, String> match : matches.entrySet()) {
            int index = match.getKey();
            int start = calculateStartIndex(index);
            int end = calculateEndIndex(index);

            snippetLength = appendWordsToSnippet(builder, start, end, snippetLength);

            if (checkLemmas) {
                Set<String> lemmasInSnippet = lemmatizer.extractLemmasFromQuery(builder.toString());
                lemmasInSnippet.retainAll(lemmas);

                if (lemmasInSnippet.size() >= 2) {
                    break;
                }
            }

            if (!checkLemmas && snippetLength >= SNIPPET_WINDOW) {
                break;
            }
        }
        return builder.toString();
    }

    // Создает сниппет на основе контента и лемм.
    private String createSnippet(String content, Set<String> lemmas) {
        this.lemmas = lemmas;
        String cleanedText = lemmatizer.cleanHtml(content);
        matches = findMatches(cleanedText);

        if (matches.isEmpty()) {
            return "";
        }

        String snippet = generateSnippet(false);
        Set<String> lemmasInSnippet = lemmatizer.extractLemmasFromQuery(snippet);
        lemmasInSnippet.retainAll(lemmas);

        if (lemmas.size() > 2 && lemmasInSnippet.size() < 2) {
            snippet = generateSnippet(true);
        }
        return snippet.trim();
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

    private SearchResult mapToSearchResult(PageEntity page, Set<String> uniqueLemmas, float absoluteRelevance,
                                           float maxRelevance) {
        float relativeRelevance = absoluteRelevance / maxRelevance;
        String title = extractTitleFromContent(page.getContent());
        String snippet = createSnippet(page.getContent(), uniqueLemmas);
        log.info("Page '{}', Max relevance '{}',  Absolute relevance '{}', Relative relevance '{}'",
                page.getPath(), maxRelevance, absoluteRelevance, relativeRelevance);

        String sizeFont = "<h3>%s</h3>";
        // Формируем HTML для заголовка и сниппета
        String formattedTitle = String.format(sizeFont, title);
        String formattedSnippet = String.format(sizeFont, snippet);

        log.info("Page '{}' - Title: '{}', Snippet: '{}', Relative Relevance: {}",
                page.getPath(), title, snippet, relativeRelevance);

        return SearchResult.builder()
                .site(page.getSite().getUrl())
                .siteName(page.getSite().getName())
                .uri(page.getPath())
                .title(formattedTitle)
                .snippet(formattedSnippet)
                .relevance(relativeRelevance)
                .build();
    }

    // Генерирует ответ для поиска.
    private SearchResponse generateSearchResponse(Set<String> uniqueLemmas) {
        float maxRelevance = Collections.max(currentPageRelevanceMap.values());
        log.info("Max relevance score: {}", maxRelevance);

        List<SearchResult> results = currentMatchingPages.stream()
                .skip(currentOffset)
                .limit(currentLimit)
                .map(page -> mapToSearchResult(page, uniqueLemmas, currentPageRelevanceMap.get(page), maxRelevance))
                .toList();
        log.info("Search completed. Total results: {}", results.size());
        return new SearchResponse(true, results.size(), results, null);
    }

    // Получает сущности лемм из репозитория.
    private List<LemmaEntity> fetchLemmaEntities(Set<String> lemmas) {
        return lemmaRepository.findByLemmaInOrderByFrequencyAsc(new ArrayList<>(lemmas));
    }

    // Получает страницы для конкретной леммы.
    private Set<PageEntity> findPagesForLemma(Set<String> lemmas) {
        log.info("Finding pages for lemmas: {} and siteEntity: {}",
                lemmas, currentSiteEntity == null ? "All sites" : currentSiteEntity.getUrl());

        List<LemmaEntity> lemmaEntities = fetchLemmaEntities(lemmas);
        log.info("Found {} lemma entities in repository.", lemmaEntities.size());

        if (currentSiteEntity != null) {
            lemmaEntities = lemmaEntities.stream()
                    .filter(lemma -> lemma.getSite().equals(currentSiteEntity))
                    .toList();
            log.info("Filtered lemma entities for site {}: {}", currentSiteEntity.getUrl(), lemmaEntities.size());
        }

        if (lemmaEntities.isEmpty()) {
            log.warn("No lemma entities found for site {} and lemmas {}",
                    currentSiteEntity == null ? "All sites" : currentSiteEntity.getUrl(), lemmas);
            return Collections.emptySet();
        }

        // Найти страницы для лемм
        Set<PageEntity> matchingPages = findMatchingPages(lemmaEntities, currentSiteEntity);
        log.info("Found {} matching pages for site {}.",
                matchingPages.size(), currentSiteEntity == null ? "All sites" : currentSiteEntity.getUrl());

        return matchingPages;
    }


    // Получает леммы для страницы.
    private Set<LemmaEntity> getLemmasForPage(PageEntity page) {
        List<IndexEntity> indexEntities = indexRepository.findByPage(page);
        log.info("Page '{}' has {} index entries.", page.getPath(), indexEntities.size());

        return indexEntities.stream()
                .map(IndexEntity::getLemma)
                .collect(Collectors.toSet());
    }

    // Получает рейтинг для леммы на странице.
    private float getRankForLemmaAndPage(LemmaEntity lemma, PageEntity page) {
        return indexRepository.findByPageAndLemma(page, lemma)
                .map(IndexEntity::getRank)  // Получаем рейтинг через Optional
                .orElse(0f);  // Если не найдено, возвращаем 0
    }

    // Вычисляет абсолютную релевантность страниц.
    private Map<PageEntity, Float> calculateAbsoluteRelevance() {
        Map<PageEntity, Float> pageRelevanceMap = new HashMap<>();
        log.info("Calculating absolute relevance for pages: {}", currentMatchingPages.size());

        for (PageEntity page : currentMatchingPages) {
            float totalRank = 0;

            Set<LemmaEntity> lemmasOnPage = getLemmasForPage(page);
            log.info("Page '{}' has {} lemmas.", page.getPath(), lemmasOnPage.size());

            for (LemmaEntity lemmaEntity : lemmasOnPage) {
                totalRank += getRankForLemmaAndPage(lemmaEntity, page);
            }
            pageRelevanceMap.put(page, totalRank);
        }
        log.info("Calculated relevance for {} pages.", pageRelevanceMap.size());
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

