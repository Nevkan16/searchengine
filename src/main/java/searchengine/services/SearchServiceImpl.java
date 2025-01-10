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

    private static final double PERCENT_THRESHOLD = 0.47;
    private static final int SNIPPET_WINDOW = 30;
    private Set<String> lemmas;
    private Map<Integer, String> matches;
    private String[] words;
    private String currentQuery;
    private int currentOffset;
    private int currentLimit;
    private Set<PageEntity> currentMatchingPages;
    private Map<PageEntity, Float> currentPageRelevanceMap;
    private Set<String> uniqueLemmas;
    private final Map<String, SearchResult> snippetCache = new HashMap<>();
    private final Set<String> uniqueSnippets = new HashSet<>();
    private float absoluteRelevance;
    private float maxRelevance;
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

            this.currentSiteEntity = siteRepository.findByUrl(site).orElse(null);
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
            long elapsedTime = System.nanoTime() - startTime;
            log.info("Search execution time: {} ms", elapsedTime / 1_000_000);
        }
    }

    // Создает пустой ответ с сообщением.
    private SearchResponse createEmptyResponse(String message) {
        log.info("Returning empty search result: {}", message);
        return new SearchResponse(true, 0, Collections.emptyList(), 0, 0, message);
    }

    // Извлекает леммы из строки запроса.
    private Set<String> extractLemmas() {
        return lemmatizer.extractLemmasFromQuery(currentQuery);
    }

    // Подсчитывает количество страниц для заданного сайта.
    private long countPages() {
        return (currentSiteEntity == null) ? pageRepository.count() : pageRepository.countBySite(currentSiteEntity);
    }

    // Получает леммы, которые нужно исключить.
    private Set<String> getExcludedLemmas(Set<String> lemmas, long totalPages) {
        Set<String> excludedLemmas = new HashSet<>();

        List<LemmaEntity> lemmaEntities = lemmaRepository.findByLemmaInOrderByFrequencyAsc(new ArrayList<>(lemmas));
        for (LemmaEntity lemmaEntity : lemmaEntities) {
            String lemma = lemmaEntity.getLemma();

            long lemmaPageCount = lemmaEntity.getIndexes().stream()
                    .map(IndexEntity::getPage)
                    .distinct()
                    .count();

            double percentage = (double) lemmaPageCount / totalPages;

            if (percentage > PERCENT_THRESHOLD) {
                if (lemmas.size() == 1) {

                    log.info("Keeping lemma '{}' because it is the only lemma in the query.", lemma);
                    continue;
                }
                excludedLemmas.add(lemma);
                log.info("Excluding lemma '{}' with frequency {} on {}% of total pages.",
                        lemma, lemmaPageCount, percentage * 100);
            }
        }
        log.info("Excluded lemmas: {}", excludedLemmas);
        return excludedLemmas;
    }

    // Фильтрует леммы в зависимости от общего количества страниц.
    private void filterLemmas(Set<String> uniqueLemmas, long totalPages) {
        Set<String> excludedLemmas = getExcludedLemmas(uniqueLemmas, totalPages);
        uniqueLemmas.removeAll(excludedLemmas);
    }

    // Обновляет список страниц на основе лемм.
    private void updatePagesBasedOnLemma(Set<PageEntity> pages, Set<PageEntity> lemmaPages) {
        if (pages.isEmpty()) {
            pages.addAll(lemmaPages);
        } else {
            Set<PageEntity> intersection = new HashSet<>(pages);
            intersection.retainAll(lemmaPages);

            if (!intersection.isEmpty()) {
                pages.retainAll(lemmaPages);
            } else {
                pages.addAll(lemmaPages);
            }
        }

        if (lemmaPages.isEmpty()) {
            pages.clear();
        }
    }

    // Получает страницы для конкретной леммы.
    private Set<PageEntity> getPagesForLemma(LemmaEntity lemmaEntity) {
        List<PageEntity> pagesList = indexRepository.findPagesByLemma(lemmaEntity);
        return new HashSet<>(pagesList);
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

            updatePagesBasedOnLemma(pages, lemmaPages);
            if (pages.isEmpty() && lemmaPages.isEmpty()) {
                break;
            }
        }
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
        int startIndex = Math.max(0, index - SNIPPET_WINDOW / 2);

        for (int i = startIndex; i < index; i++) {
            if (Character.isUpperCase(words[i].charAt(0)) && i + 1 < words.length) {
                if (!isEndOfSentence(words[i + 1])) {
                    return i;
                }
            }
        }

        return startIndex;
    }

    // Проверка, является ли слово знаком окончания предложения.
    private boolean isEndOfSentence(String word) {
        return word.equals(".") || word.equals("!") || word.equals("?");
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

    private String createSnippet(String content, Set<String> lemmas) {
        String cacheKey = content.hashCode() + "_" + lemmas.hashCode();

        if (snippetCache.containsKey(cacheKey)) {
            return snippetCache.get(cacheKey).getSnippet();
        }

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

        if (!uniqueSnippets.contains(snippet.trim())) {
            uniqueSnippets.add(snippet.trim());
        } else {
            snippet = "";
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

    // Преобразует страницу в результат поиска.
    private SearchResult mapToSearchResult(PageEntity page) {
        String cacheKey = page.getContent().hashCode() + "_" + uniqueLemmas.hashCode();

        if (snippetCache.containsKey(cacheKey)) {
            return snippetCache.get(cacheKey);
        }

        float relativeRelevance = absoluteRelevance / maxRelevance;
        String title = extractTitleFromContent(page.getContent());
        String snippet = createSnippet(page.getContent(), uniqueLemmas);

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

    // Генерирует ответ для поиска.
    private SearchResponse generateSearchResponse(Set<String> uniqueLemmas) {
        this.uniqueLemmas = uniqueLemmas;
        this.maxRelevance = Collections.max(currentPageRelevanceMap.values());

        Set<String> processedSnippets = new HashSet<>();
        List<SearchResult> results = currentMatchingPages
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
                .skip(currentOffset)
                .limit(currentLimit)
                .toList();

        int totalResults = processedSnippets.size();
        int totalPages = (int) Math.ceil((double) totalResults / currentLimit);
        int currentPage = (currentOffset / currentLimit) + 1;

        return new SearchResponse(true, totalResults, results, currentPage, totalPages, null);
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

        if (currentSiteEntity != null) {
            lemmaEntities = lemmaEntities.stream()
                    .filter(lemma -> lemma.getSite().equals(currentSiteEntity))
                    .toList();
        }

        if (lemmaEntities.isEmpty()) {
            return Collections.emptySet();
        }

        return findMatchingPages(lemmaEntities, currentSiteEntity);
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
        Map<PageEntity, Float> pageRelevanceMap = new HashMap<>();

        for (PageEntity page : currentMatchingPages) {
            float totalRank = 0;

            Set<LemmaEntity> lemmasOnPage = getLemmasForPage(page);

            for (LemmaEntity lemmaEntity : lemmasOnPage) {
                totalRank += getRankForLemmaAndPage(page, lemmaEntity);
            }
            pageRelevanceMap.put(page, totalRank);
        }
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