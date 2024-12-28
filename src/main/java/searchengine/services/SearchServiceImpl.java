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

    private Set<String> extractLemmas(String query) {
        Set<String> uniqueLemmas = lemmatizer.extractLemmasFromQuery(query);
        log.info("Extracted Lemmas: {}", uniqueLemmas);
        return uniqueLemmas;
    }

    private SiteEntity validateSite(String site) {
        if (site == null) {
            return null;
        }
        return siteRepository.findByUrl(site).orElse(null);
    }

    private long countPages(SiteEntity siteEntity) {
        long totalPages = (siteEntity == null) ? pageRepository.count() : pageRepository.countBySite(siteEntity);
        log.info("Total pages for search: {}", totalPages);
        return totalPages;
    }

    private void filterLemmas(Set<String> uniqueLemmas, long totalPages) {
        Set<String> excludedLemmas = getExcludedLemmas(uniqueLemmas, totalPages);
        uniqueLemmas.removeAll(excludedLemmas);
        log.info("Remaining Lemmas after exclusion: {}", uniqueLemmas);
    }

    private SearchResponse createEmptyResponse(String message) {
        log.info("Returning empty search result: {}", message);
        return new SearchResponse(true, 0, Collections.emptyList(), message);
    }

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

    private String extractTitleFromContent(String content) {
        int titleStart = content.indexOf("<title>");
        int titleEnd = content.indexOf("</title>");
        if (titleStart != -1 && titleEnd != -1) {
            return content.substring(titleStart + 7, titleEnd).trim();
        }
        return "No title";
    }

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

    private Set<LemmaEntity> getLemmasForPage(PageEntity page) {
        Set<LemmaEntity> lemmasOnPage = new HashSet<>();

        List<IndexEntity> indexEntities = indexRepository.findByPage(page);
        log.info("Page '{}' has {} index entries.", page.getPath(), indexEntities.size());

        for (IndexEntity indexEntity : indexEntities) {
            lemmasOnPage.add(indexEntity.getLemma());
        }

        return lemmasOnPage;
    }

    private float getRankForLemmaAndPage(LemmaEntity lemma, PageEntity page) {
        Optional<IndexEntity> indexEntityOpt = indexRepository.findByPageAndLemma(page, lemma);
        if (indexEntityOpt.isPresent()) {
            return indexEntityOpt.get().getRank();
        }
        return 0f;
    }

    private Set<PageEntity> findPagesForLemma(Set<String> lemmas, SiteEntity siteEntity) {
        Set<PageEntity> pages = new HashSet<>();
        log.info("Finding pages for lemmas: {} and siteEntity: {}",
                lemmas, siteEntity == null ? "All sites" : siteEntity.getUrl());

        List<LemmaEntity> lemmaEntities = lemmaRepository.findByLemmaInOrderByFrequencyAsc(new ArrayList<>(lemmas));
        log.info("Found {} lemma entities in repository.", lemmaEntities.size());

        for (LemmaEntity lemmaEntity : lemmaEntities) {
            Set<PageEntity> lemmaPages = getPagesForLemma(lemmaEntity);

            if (siteEntity != null) {
                lemmaPages = lemmaPages.stream()
                        .filter(pageEntity -> pageEntity.getSite()
                                .equals(siteEntity))
                        .collect(Collectors.toSet());
            }

            if (pages.isEmpty()) {
                pages.addAll(lemmaPages);
            } else {
                log.info("Pages for lemma '{}': {}", lemmaEntity.getLemma(), lemmaPages.stream()
                        .map(PageEntity::getPath).toList());
                pages.retainAll(lemmaPages);
                log.info("Pages after intersection: {}", pages.stream().map(PageEntity::getPath).toList());

            }

            if (pages.isEmpty()) {
                pages.addAll(lemmaPages);
            } else if (lemmaPages.isEmpty()) {
                pages.clear();
            } else {
                pages.retainAll(lemmaPages);
            }
        }

        log.info("Found {} matching pages for lemmas.", pages.size());
        return pages;
    }

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

    private Set<String> getExcludedLemmas(Set<String> lemmas, long totalPages) {
        Set<String> excludedLemmas = new HashSet<>();
        log.info("Filtering excluded lemmas. Total pages: {}", totalPages);

        List<LemmaEntity> lemmaEntities = lemmaRepository.findByLemmaInOrderByFrequencyAsc(new ArrayList<>(lemmas));

        for (LemmaEntity lemmaEntity : lemmaEntities) {
            long lemmaPageCount = lemmaEntity.getIndexes().stream()
                    .map(IndexEntity::getPage)
                    .distinct()
                    .count();

            // Если лемма встречается на большем проценте страниц, чем допустимый порог, она будет исключена
            double percentage = (double) lemmaPageCount / totalPages;

            // Порог для исключения лемм (можно настроить)
            if (percentage > PERCENT_THRESHOLD) {
                excludedLemmas.add(lemmaEntity.getLemma());
                log.info("Excluding lemma '{}' with frequency {} on {}% of total pages.",
                        lemmaEntity.getLemma(), lemmaPageCount, percentage * 100);
            }
        }

        log.info("Excluded lemmas: {}", excludedLemmas);
        return excludedLemmas;
    }


    private String createSnippet(String content, Set<String> lemmas, Lemmatizer lemmatizer) {
        final int SNIPPET_WINDOW = 30;
        Map<Integer, String> matches = new TreeMap<>();
        String[] words = content.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^а-яА-Яa-zA-Z0-9]", "").toLowerCase();
            Set<String> wordLemmas = lemmatizer.extractLemmasFromQuery(word);
            if (!Collections.disjoint(lemmas, wordLemmas)) {
                matches.put(i, words[i]);
            }
        }

        if (matches.isEmpty()) {
            return "";
        }

        StringBuilder snippet = new StringBuilder();
        int snippetLength = 0;
        for (Map.Entry<Integer, String> match : matches.entrySet()) {
            int index = match.getKey();
            int start = Math.max(0, index - SNIPPET_WINDOW / 2);
            int end = Math.min(words.length, index + SNIPPET_WINDOW / 2);
            for (int i = start; i < end && snippetLength < SNIPPET_WINDOW; i++) {
                String word = words[i];
                Set<String> wordLemmas = lemmatizer.extractLemmasFromQuery
                        (word.replaceAll
                                ("[^а-яА-Яa-zA-Z0-9]", "").toLowerCase());

                if (!Collections.disjoint(lemmas, wordLemmas)) {
                    snippet.append("<b>").append(word).append("</b> ");
                } else {
                    snippet.append(word).append(" ");
                }
                snippetLength++;
            }
            if (snippetLength >= SNIPPET_WINDOW) {
                break;
            }
        }

        return snippet.toString().trim();
    }
}

