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
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueryUtil {
    private final LemmaRepository lemmaRepository;
    private final Lemmatizer lemmatizer;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private static final double THRESHOLD_PERCENTAGE = 75;

    public List<String> formattedQuery(String query) {
        Set<String> extractedLemmas = extractLemmas(query);
        List<LemmaEntity> lemmaEntities = fetchLemmaEntities((extractedLemmas));
        List<LemmaEntity> agg = aggregateLemmaFrequencies(lemmaEntities);
        List<String> sortedLemmaNames = getSortedLemmaNamesFromEntities(agg);
        return filterLemmasByPercentage(sortedLemmaNames);
    }

    public Set<PageEntity> findMatchingPages(String query, SiteEntity siteEntity) {
        List<String> formatted = formattedQuery(query);
        Set<PageEntity> getPageFromSite = getPagesMatchingAllLemmas(formatted);
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

    private List<String> filterLemmasByPercentage(List<String> sortedLemmaNames) {
        long totalPageCount = pageRepository.count();

        if (totalPageCount == 0) {
            return Collections.emptyList();
        }

        List<String> filteredLemmaNames = new ArrayList<>();

        for (String lemmaName : sortedLemmaNames) {
            List<LemmaEntity> lemmaEntities = getLemmasByName(lemmaName);
            if (!lemmaEntities.isEmpty()) {
                int totalFrequency = lemmaEntities
                        .stream()
                        .mapToInt(LemmaEntity::getFrequency)
                        .sum();

                double percentage = ((double) totalFrequency / totalPageCount) * 100;

                if (percentage <= THRESHOLD_PERCENTAGE) {
                    filteredLemmaNames.add(lemmaName); // Добавляем слово, если процент <= порога
                } else {
                    log.info("Лемма '{}' превышает порог {}% ({}%). Удаляется из списка.",
                            lemmaName, THRESHOLD_PERCENTAGE, String.format(Locale.US, "%.2f", percentage));
                }
            } else {
                log.info("Для леммы '{}' не найдено соответствующих записей. Она также удаляется из списка.", lemmaName);
            }
        }

        return filteredLemmaNames;
    }

    private Set<PageEntity> getPagesMatchingAllLemmas(List<String> sortedLemmaNames) {
        if (sortedLemmaNames.isEmpty()) {
            return Collections.emptySet(); // Возвращаем пустое множество
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

        List<String> remainingLemmaNames = sortedLemmaNames.subList(1, sortedLemmaNames.size());


        return pages.parallelStream()
                .filter(page -> doesPageContainAllLemmas(page, remainingLemmaNames))
                .collect(Collectors.toSet());
    }



    private boolean doesPageContainAllLemmas(PageEntity page, List<String> remainingLemmaNames) {
        for (String lemmaName : remainingLemmaNames) {
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

    private Set<String> extractLemmas(String query) {
        return lemmatizer.extractLemmasFromQuery(query);
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
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency)) // Сортировка по частоте
                .collect(Collectors.toList());
    }
}
