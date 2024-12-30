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

    private final Lemmatizer lemmatizer;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        log.info("Starting search with query: '{}', site: '{}', offset: {}, limit: {}", query, site, offset, limit);

        Set<String> uniqueLemmas = extractLemmas(query);
        if (uniqueLemmas.isEmpty()) {
            return createEmptyResponse("No valid lemmas found.");
        }

        SiteEntity siteEntity = validateSite(site);
        if (site != null && siteEntity == null) {
            return createEmptyResponse("Site not found");
        }

        long totalPages = countPages(siteEntity);
        filterLemmas(uniqueLemmas, totalPages);

        if (uniqueLemmas.isEmpty()) {
            return createEmptyResponse(null);
        }

        return processSearchResults(uniqueLemmas, siteEntity, offset, limit);
    }

    // Извлекает леммы из строки запроса.
    private Set<String> extractLemmas(String query) {
        Set<String> uniqueLemmas = lemmatizer.extractLemmasFromQuery(query);
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
    private long countPages(SiteEntity siteEntity) {
        long totalPages = (siteEntity == null) ? pageRepository.count() : pageRepository.countBySite(siteEntity);
        log.info("Total pages for search: {}", totalPages);
        return totalPages;
    }

    // Фильтрует леммы в зависимости от общего количества страниц.
    private void filterLemmas(Set<String> uniqueLemmas, long totalPages) {
        Set<String> excludedLemmas = getExcludedLemmas(uniqueLemmas, totalPages);
        uniqueLemmas.removeAll(excludedLemmas);
        log.info("Remaining Lemmas after exclusion: {}", uniqueLemmas);
    }

    // Создает пустой ответ с сообщением.
    private SearchResponse createEmptyResponse(String message) {
        log.info("Returning empty search result: {}", message);
        return new SearchResponse(true, 0, Collections.emptyList(), message);
    }

    // Обрабатывает результаты поиска.
    private SearchResponse processSearchResults(Set<String> uniqueLemmas, SiteEntity siteEntity,
                                                int offset,
                                                int limit) {
        Set<PageEntity> matchingPages = findPagesForLemma(uniqueLemmas, siteEntity);
        log.info("Found matching pages: {}", matchingPages.size());

        if (matchingPages.isEmpty()) {
            return createEmptyResponse(null);
        }

        Map<PageEntity, Float> pageRelevanceMap = calculateAbsoluteRelevance(matchingPages);
        if (pageRelevanceMap.isEmpty()) {
            return createEmptyResponse("No relevance data found.");
        }

        return generateSearchResponse(matchingPages, pageRelevanceMap, uniqueLemmas, offset, limit);
    }

    // Генерирует ответ для поиска.
    private SearchResponse generateSearchResponse(Set<PageEntity> matchingPages, Map<PageEntity,
            Float> pageRelevanceMap, Set<String> uniqueLemmas, int offset, int limit) {
        float maxRelevance = Collections.max(pageRelevanceMap.values());
        log.info("Max relevance score: {}", maxRelevance);

        List<SearchResult> results = matchingPages.stream()
                .skip(offset)
                .limit(limit)
                .map(page -> mapToSearchResult(page, uniqueLemmas, pageRelevanceMap.get(page), maxRelevance))
                .toList();

        log.info("Search completed. Total results: {}", results.size());
        return new SearchResponse(true, results.size(), results, null);
    }


    // Преобразует страницу в результат поиска.
    private SearchResult mapToSearchResult(PageEntity page, Set<String> uniqueLemmas, float absoluteRelevance,
                                           float maxRelevance) {
        float relativeRelevance = absoluteRelevance / maxRelevance;
        String title = extractTitleFromContent(page.getContent());
        String snippet = createSnippet(page.getContent(), uniqueLemmas, lemmatizer);

        log.info("Page '{}' - Title: '{}', Snippet: '{}', Relative Relevance: {}",
                page.getPath(), title, snippet, relativeRelevance);

        return SearchResult.builder()
                .site(page.getSite().getUrl())
                .siteName(page.getSite().getName())
                .uri(page.getPath())
                .title(title)
                .snippet(snippet)
                .relevance(relativeRelevance)
                .build();
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

    // Вычисляет абсолютную релевантность страниц.
    private Map<PageEntity, Float> calculateAbsoluteRelevance(Set<PageEntity> matchingPages) {
        Map<PageEntity, Float> pageRelevanceMap = new HashMap<>();
        log.info("Calculating absolute relevance for pages: {}", matchingPages.size());

        for (PageEntity page : matchingPages) {
            float totalRank = 0;

            Set<LemmaEntity> lemmasOnPage = getLemmasForPage(page);
            log.info("Page '{}' has {} lemmas.", page.getPath(), lemmasOnPage.size());

            for (LemmaEntity lemma : lemmasOnPage) {
                totalRank += getRankForLemmaAndPage(lemma, page);
            }

            pageRelevanceMap.put(page, totalRank);
        }

        log.info("Calculated relevance for {} pages.", pageRelevanceMap.size());
        return pageRelevanceMap;
    }

    // Получает леммы для страницы.
    private Set<LemmaEntity> getLemmasForPage(PageEntity page) {
        Set<LemmaEntity> lemmasOnPage = new HashSet<>();

        List<IndexEntity> indexEntities = indexRepository.findByPage(page);
        log.info("Page '{}' has {} index entries.", page.getPath(), indexEntities.size());

        for (IndexEntity indexEntity : indexEntities) {
            lemmasOnPage.add(indexEntity.getLemma());
        }

        return lemmasOnPage;
    }

    // Получает рейтинг для леммы на странице.
    private float getRankForLemmaAndPage(LemmaEntity lemma, PageEntity page) {
        Optional<IndexEntity> indexEntityOpt = indexRepository.findByPageAndLemma(page, lemma);
        if (indexEntityOpt.isPresent()) {
            return indexEntityOpt.get().getRank();
        }
        return 0f;
    }

    // Находит страницы для заданных лемм и сайта.
    private Set<PageEntity> findPagesForLemma(Set<String> lemmas, SiteEntity siteEntity) {
        log.info("Finding pages for lemmas: {} and siteEntity: {}",
                lemmas, siteEntity == null ? "All sites" : siteEntity.getUrl());

        List<LemmaEntity> lemmaEntities = fetchLemmaEntities(lemmas);
        log.info("Found {} lemma entities in repository.", lemmaEntities.size());

        return findMatchingPages(lemmaEntities, siteEntity);
    }

    // Получает сущности лемм из репозитория.
    private List<LemmaEntity> fetchLemmaEntities(Set<String> lemmas) {
        // Получение списка лемм из репозитория, упорядоченных по частоте
        return lemmaRepository.findByLemmaInOrderByFrequencyAsc(new ArrayList<>(lemmas));
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

    // Фильтрует страницы по сайту.
    private Set<PageEntity> filterPagesBySite(Set<PageEntity> lemmaPages, SiteEntity siteEntity) {
        if (siteEntity == null) {
            return lemmaPages;
        }

        return lemmaPages.stream()
                .filter(pageEntity -> pageEntity.getSite().equals(siteEntity))
                .collect(Collectors.toSet());
    }

    // Обновляет список страниц на основе лемм.
    private void updatePagesBasedOnLemma
            (Set<PageEntity> pages, Set<PageEntity> lemmaPages, LemmaEntity lemmaEntity) {
        if (pages.isEmpty()) {
            pages.addAll(lemmaPages);
        } else {
            log.info("Pages for lemma '{}': {}", lemmaEntity.getLemma(),
                    lemmaPages.stream().map(PageEntity::getPath).toList());
            pages.retainAll(lemmaPages); // Пересечение страниц
            log.info("Pages after intersection: {}", pages.stream().map(PageEntity::getPath).toList());
        }

        if (lemmaPages.isEmpty()) {
            pages.clear(); // Если lemmaPages пуст, очищаем все результаты
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

    // Создает сниппет на основе контента и лемм.
    private String createSnippet(String content, Set<String> lemmas, Lemmatizer lemmatizer) {
        // Найти совпадения лемм в тексте
        Map<Integer, String> matches = findMatches(content, lemmas, lemmatizer);

        if (matches.isEmpty()) {
            return "";
        }

        // Построить сниппет на основе найденных совпадений
        String snippet = buildSnippet(content.split("\\s+"), matches, lemmas, lemmatizer);

        // Проверяем, содержит ли сниппет как минимум 2 уникальные леммы из запроса
        Set<String> lemmasInSnippet = lemmatizer.extractLemmasFromQuery(snippet);
        lemmasInSnippet.retainAll(lemmas); // Оставляем только пересечения

        if (lemmas.size() > 2 && lemmasInSnippet.size() < 2) {
            // Если недостаточно лемм, ищем другой фрагмент
            snippet = findBetterSnippet(content, matches, lemmas, lemmatizer);
        }

        return snippet.trim();
    }

    // Находит лучший сниппет, если предыдущий не подходит.
    private String findBetterSnippet(String content, Map<Integer, String> matches,
                                     Set<String> lemmas, Lemmatizer lemmatizer) {
        String[] words = content.split("\\s+");
        StringBuilder snippet = new StringBuilder();
        int snippetLength = 0;

        // Проходим по всем совпадениям и собираем более релевантный сниппет
        for (Map.Entry<Integer, String> match : matches.entrySet()) {
            int index = match.getKey();
            int start = calculateStartIndex(index);
            int end = calculateEndIndex(index, words.length);

            snippetLength = appendWordsToSnippet(snippet, words, start, end, lemmas, lemmatizer, snippetLength);

            // Проверяем количество лемм в текущем сниппете
            Set<String> lemmasInSnippet = lemmatizer.extractLemmasFromQuery(snippet.toString());
            lemmasInSnippet.retainAll(lemmas);

            if (lemmasInSnippet.size() >= 2) {
                break;
            }
        }

        return snippet.toString();
    }

    // Находит совпадения лемм в тексте.
    private Map<Integer, String> findMatches(String content, Set<String> lemmas, Lemmatizer lemmatizer) {
        Map<Integer, String> matches = new TreeMap<>();
        String[] words = content.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = cleanWord(words[i]);
            Set<String> wordLemmas = lemmatizer.extractLemmasFromQuery(word);
            if (!Collections.disjoint(lemmas, wordLemmas)) {
                matches.put(i, words[i]);
            }
        }

        return matches;
    }

    // Строит сниппет на основе найденных совпадений.
    private String buildSnippet(String[] words, Map<Integer, String> matches,
                                Set<String> lemmas, Lemmatizer lemmatizer) {
        StringBuilder snippet = new StringBuilder();
        int snippetLength = 0;

        for (Map.Entry<Integer, String> match : matches.entrySet()) {
            int index = match.getKey();
            int start = calculateStartIndex(index);
            int end = calculateEndIndex(index, words.length);

            snippetLength = appendWordsToSnippet(snippet, words, start, end, lemmas, lemmatizer, snippetLength);
            if (snippetLength >= SNIPPET_WINDOW) {
                break;
            }
        }

        return snippet.toString().trim();
    }

    // Вычисляет начальный индекс для сниппета.
    private int calculateStartIndex(int index) {
        return Math.max(0, index - SNIPPET_WINDOW / 2);
    }

    // Вычисляет конечный индекс для сниппета.
    private int calculateEndIndex(int index, int wordsLength) {
        return Math.min(wordsLength, index + SNIPPET_WINDOW / 2);
    }

    // Добавляет слова в сниппет.
    private int appendWordsToSnippet(StringBuilder snippet, String[] words, int start, int end,
                                     Set<String> lemmas, Lemmatizer lemmatizer, int snippetLength) {
        for (int i = start; i < end && snippetLength < SNIPPET_WINDOW; i++) {
            String word = words[i];
            if (isWordRelevant(word, lemmas, lemmatizer)) {
                snippet.append("<b>").append(word).append("</b> ");
            } else {
                snippet.append(word).append(" ");
            }
            snippetLength++;
        }
        return snippetLength;
    }

    // Проверяет, является ли слово релевантным для лемм.
    private boolean isWordRelevant(String word, Set<String> lemmas, Lemmatizer lemmatizer) {
        String cleanedWord = cleanWord(word);
        Set<String> wordLemmas = lemmatizer.extractLemmasFromQuery(cleanedWord);
        return !Collections.disjoint(lemmas, wordLemmas);
    }

    // Очищает слово от нежелательных символов.
    private String cleanWord(String word) {
        return word.replaceAll("[^а-яА-Яa-zA-Z0-9]", "").toLowerCase();
    }
}

