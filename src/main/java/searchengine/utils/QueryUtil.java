package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueryUtil {
    private final LemmaRepository lemmaRepository;
    private final LemmatizerUtil lemmatizerUtil;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private static final double THRESHOLD_PERCENTAGE = 75;
    private final ConcurrentHashMap<String, Set<String>> lemmaCache = new ConcurrentHashMap<>();

    public List<String> getPresentSortedLemmaNames(String query) {
        Set<String> extractedLemmasNames = extractLemmas(query);
        Set<String> afterExtractingLemmas = filterLemmasByPercentage(extractedLemmasNames);
        List<LemmaEntity> lemmaEntities = fetchLemmaEntities((afterExtractingLemmas));
        List<LemmaEntity> agg = aggregateLemmaFrequencies(lemmaEntities);
        return getSortedLemmaNamesFromEntities(agg);
    }

    public Set<PageEntity> findMatchingPages(String query, SiteEntity siteEntity) {
        List<String> formatted = getPresentSortedLemmaNames(query);
        Set<PageEntity> getPageFromSite = getPagesMatchingAllLemmas(formatted, query);
        return filterPagesBySite(getPageFromSite, siteEntity);
    }

    // Фильтрует страницы по сайту.
    private Set<PageEntity> filterPagesBySite(Set<PageEntity> pageEntities, SiteEntity siteEntity) {
        if (siteEntity == null) {
            return pageEntities;
        }
        return pageEntities.stream()
                .filter(pageEntity -> pageEntity.getSite().equals(siteEntity))
                .collect(Collectors.toSet());
    }

    private Set<String> filterLemmasByPercentage(Set<String> queryLemmas) {
        long totalPageCount = pageRepository.count();

        if (totalPageCount == 0) {
            return Collections.emptySet();
        }
        Set<String> filteredLemmaNames = new HashSet<>();

        for (String lemmaName : queryLemmas) {
            List<LemmaEntity> lemmaEntities = getLemmasByName(lemmaName);
            if (!lemmaEntities.isEmpty()) {
                int totalFrequency = lemmaEntities.stream()
                        .mapToInt(LemmaEntity::getFrequency)
                        .sum();
                double percentage = ((double) totalFrequency / totalPageCount) * 100;
                if (percentage <= THRESHOLD_PERCENTAGE) {
                    filteredLemmaNames.add(lemmaName);
                } else {
                    log.info("Лемма '{}' превышает порог {}% ({}%). Удаляется из списка.",
                            lemmaName, THRESHOLD_PERCENTAGE, String.format(Locale.US, "%.2f", percentage));
                }
            } else {
                log.info("Для леммы '{}' не найдено соответствующих записей.", lemmaName);
            }
        }

        return filteredLemmaNames;
    }

    private Set<PageEntity> getPagesMatchingAllLemmas(List<String> sortedLemmaNames, String query) {
        Set<String> extractedLemmasNames = extractLemmas(query);

        if (sortedLemmaNames.isEmpty() || extractedLemmasNames.isEmpty()) {
            return Collections.emptySet();
        }

        String firstLemmaName = sortedLemmaNames.get(0);
        List<LemmaEntity> firstLemmaEntities = getLemmasByName(firstLemmaName);

        if (firstLemmaEntities.isEmpty()) {
            return Collections.emptySet();
        }

        Set<PageEntity> pages = new HashSet<>();
        for (LemmaEntity lemmaEntity : firstLemmaEntities) {
            pages.addAll(indexRepository.findPagesByLemma(lemmaEntity));
        }

        return pages.parallelStream()
                .filter(page -> doesPageContainAllExtractedLemmas(page, extractedLemmasNames))
                .collect(Collectors.toSet());
    }

    private boolean doesPageContainAllExtractedLemmas(PageEntity page, Set<String> extractedLemmasNames) {
        for (String lemmaName : extractedLemmasNames) {
            List<LemmaEntity> lemmas = getLemmasByName(lemmaName);

            boolean pageContainsLemma = lemmas.stream()
                    .anyMatch(lemma -> indexRepository.existsByPageAndLemma(page, lemma));

            if (!pageContainsLemma) {
                return false;
            }
        }
        return true;
    }

    private List<LemmaEntity> getLemmasByName(String lemmaName) {
        return lemmaRepository.findByLemma(lemmaName);
    }

    private List<String> getSortedLemmaNamesFromEntities(List<LemmaEntity> lemmaEntities) {
        return lemmaEntities.stream()
                .collect(Collectors.groupingBy(
                        LemmaEntity::getLemma,
                        Collectors.summingInt(LemmaEntity::getFrequency)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public Set<String> extractLemmas(String query) {
        int MAX_CACHE_SIZE = 30000;
        synchronized (lemmaCache) {
            if (lemmaCache.size() >= MAX_CACHE_SIZE) {
                log.warn("Cache size limit reached. Clearing cache...");
                lemmaCache.clear();
            }
        }
        return lemmaCache.computeIfAbsent(query, lemmatizerUtil::extractLemmasFromQuery);
    }

    private List<LemmaEntity> fetchLemmaEntities(Set<String> lemmas) {
        return lemmaRepository.findByLemmaInOrderByFrequencyAsc(new ArrayList<>(lemmas));
    }

    private List<LemmaEntity> aggregateLemmaFrequencies(List<LemmaEntity> lemmaEntities) {
        Map<String, Integer> frequencyMap = new HashMap<>();

        for (LemmaEntity lemmaEntity : lemmaEntities) {
            frequencyMap.merge(lemmaEntity.getLemma(), lemmaEntity.getFrequency(), Integer::sum);
        }

        return frequencyMap.entrySet().stream()
                .map(entry -> {
                    LemmaEntity aggregatedLemma = new LemmaEntity();
                    aggregatedLemma.setLemma(entry.getKey());
                    aggregatedLemma.setFrequency(entry.getValue());
                    return aggregatedLemma;
                })
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                .collect(Collectors.toList());
    }
}