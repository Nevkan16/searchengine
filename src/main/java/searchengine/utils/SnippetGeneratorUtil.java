package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class SnippetGeneratorUtil {
    private final LemmatizerUtil lemmatizerUtil;
    private static final int SNIPPET_WINDOW = 220;
    private final QueryUtil queryUtil;
    private final Map<String, List<Map.Entry<String, Set<String>>>> queryLemmasCache = new ConcurrentHashMap<>();
    private Map<Integer, Set<String>> result;
    private List<Set<String>> uniqueValuesList;
    private ResultData resultData;

    // Класс для хранения минимальной разницы и ключей
    static class ResultData {
        Integer finalUpperKey = null;
        Integer finalLowerKey = null;
        int minDivided = Integer.MAX_VALUE;

        void update(Integer upperKey, Integer lowerKey, int divided) {
            if (divided < minDivided) {
                minDivided = divided;
                finalUpperKey = upperKey;
                finalLowerKey = lowerKey;
            }
        }
    }

    public String generateSnippet(String content, String query) {
        String cleanedText = lemmatizerUtil.cleanHtml(content);
        cleanedText = cleanHtmlTags(cleanedText);

        List<Map.Entry<String, Set<String>>> queryMap = getCachedQueryLemmas(query);
        List<Map.Entry<String, Set<String>>> wordLemmasList = getWordLemmasList(cleanedText);
        Map<Integer, Set<String>> intersectionMap = getIntersectionMap(queryMap, wordLemmasList);
        Set<String> minCount = getValueWithMinimalOccurrences(intersectionMap);
        Map<Integer, Set<String>> resultMap = countMinSize(minCount, intersectionMap);
        Map<Integer, Set<String>> rebuiltMap = rebuildResultMap(intersectionMap, resultMap);

        String snippet = extractTextFragments(rebuiltMap, cleanedText);

        List<Map.Entry<String, Set<String>>> extractedTextMap = getWordLemmasList(snippet);

        if (!isQueryCovered(queryMap, extractedTextMap)) {
            return "";
        }

        return highlightKeywords(snippet, query);
    }

    private List<Map.Entry<String, Set<String>>> getCachedQueryLemmas(String query) {
        final int MAX_CACHE_SIZE = 100;

        synchronized (queryLemmasCache) {
            if (queryLemmasCache.size() > MAX_CACHE_SIZE) {
                log.warn("Cache size exceeded {}. Clearing cache...", MAX_CACHE_SIZE);
                queryLemmasCache.clear();
            }
        }
        return queryLemmasCache.computeIfAbsent(query, this::getWordLemmasList);
    }

    private boolean isQueryCovered(List<Map.Entry<String, Set<String>>> queryMap,
                                   List<Map.Entry<String, Set<String>>> extractedTextMap) {
        Set<String> queryLemmas = queryMap.stream()
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toSet());

        // Собираем уникальные значения из extractedTextMap
        Set<String> extractedLemmas = extractedTextMap.stream()
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toSet());

        return extractedLemmas.containsAll(queryLemmas);
    }

    private String cleanHtmlTags(String input) {
        return input.replaceAll("<[^>]*>", "");
    }

    // Метод для получения списка исходных слов и их уникальных лемм с сохранением порядка
    private List<Map.Entry<String, Set<String>>> getWordLemmasList(String text) {
        String[] words = text.split("\\s+");
        List<Map.Entry<String, Set<String>>> wordLemmasList = new ArrayList<>();

        for (String word : words) {
            word = word.toLowerCase();
            Set<String> lemmas = new HashSet<>(lemmatizerUtil.extractLemmasFromQuery(word));
            wordLemmasList.add(new AbstractMap.SimpleEntry<>(word, lemmas));
        }

        return wordLemmasList;
    }

    private String extractTextFragments(Map<Integer, Set<String>> rebuildResultMap, String text) {
        String[] words = text.split("\\s+");

        if (rebuildResultMap.isEmpty()) {
            return "";
        }

        int index = rebuildResultMap.keySet().iterator().next();

        return buildSnippetAroundIndex(words, index);
    }

    // Метод для построения фрагмента вокруг индекса
    private String buildSnippetAroundIndex(String[] words, int index) {
        int start = index;
        int end = index;

        int charCount = calculateCharCount(words, start, end);
        while (charCount < SNIPPET_WINDOW) {
            boolean expanded = false;

            if (start > 0) {  // Расширяем влево
                start--;
                charCount = calculateCharCount(words, start, end);
                expanded = true;
            }

            if (end < words.length - 1) { // Расширяем вправо
                end++;
                charCount = calculateCharCount(words, start, end);
                expanded = true;
            }

            if (!expanded) {
                break;
            }
            if ((end - start + 1) > SNIPPET_WINDOW) {
                return "";
            }
        }

        return formatSnippet(words, start, end);
    }

    private String formatSnippet(String[] words, int start, int end) {
        StringBuilder snippet = new StringBuilder();

        if (start > 0) snippet.append("... ");
        for (int i = start; i <= end; i++) {
            snippet.append(words[i]).append(" ");
        }
        if (end < words.length - 1) snippet.append("...");

        return snippet.toString().trim();
    }

    // Подсчёт символов в диапазоне
    private int calculateCharCount(String[] words, int start, int end) {
        int count = 0;
        for (int i = start; i <= end; i++) {
            count += words[i].length() + 1;
        }
        return count;
    }

    public Map<Integer, Set<String>> rebuildResultMap(Map<Integer, Set<String>> inputResult, Map<Integer,
            Set<String>> resultMap) {
        this.result = inputResult;
        this.uniqueValuesList = new ArrayList<>(new LinkedHashSet<>(result.values()));
        this.resultData = new ResultData();

        Map<Integer, Set<String>> rebuiltMap = new LinkedHashMap<>();

        if (uniqueValuesList.size() == 1) {
            Integer singleKey = result.keySet().iterator().next();
            rebuiltMap.put(singleKey, result.get(singleKey));
            return rebuiltMap;
        }

        List<Integer> sortedKeysDescending = new ArrayList<>(resultMap.keySet());
        sortedKeysDescending.sort(Collections.reverseOrder());

        List<Integer> sortedKeysAscending = new ArrayList<>(resultMap.keySet());
        sortedKeysAscending.sort(Comparator.naturalOrder());

        processDescending(sortedKeysDescending);
        processAscending(sortedKeysAscending);

        if (resultData.finalUpperKey != null && resultData.finalLowerKey != null) {
            rebuiltMap.put(resultData.finalUpperKey, result.get(resultData.finalUpperKey));
            rebuiltMap.put(resultData.finalLowerKey, result.get(resultData.finalLowerKey));
        }

        return rebuiltMap;
    }

    private void processKeys(List<Integer> sortedKeys, boolean ascending) {
        for (Integer startKey : sortedKeys) {
            Integer currentKey = startKey;
            Integer boundaryKey = null;
            Set<Set<String>> currentUniqueValues = new HashSet<>();

            if (result.containsKey(currentKey)) {
                currentUniqueValues.add(result.get(currentKey));
            }

            while ((currentKey = getNextKey(currentKey, ascending)) != null) {
                Set<String> currentValue = result.get(currentKey);
                if (currentValue != null && currentUniqueValues.add(currentValue)) {
                    boundaryKey = currentKey;
                }

                if (currentUniqueValues.size() == uniqueValuesList.size()) {
                    if (boundaryKey != null) {
                        // Рассчитываем границы диапазона
                        int lowerKey = ascending ? startKey : boundaryKey;
                        int upperKey = ascending ? boundaryKey : startKey;
                        int range = upperKey - lowerKey;

                        resultData.update(upperKey, lowerKey, range);
                    }
                    break;
                }
            }
        }
    }

    private Integer getNextKey(Integer currentKey, boolean ascending) {
        TreeMap<Integer, Set<String>> treeMap = new TreeMap<>(result);
        return ascending ? treeMap.higherKey(currentKey) : treeMap.lowerKey(currentKey);
    }

    private void processDescending(List<Integer> sortedKeysDescending) {
        processKeys(sortedKeysDescending, false);
    }

    private void processAscending(List<Integer> sortedKeysAscending) {
        processKeys(sortedKeysAscending, true);
    }

    // Карта которая хранит min значение со всеми индексами
    private Map<Integer, Set<String>> countMinSize(Set<String> minCount, Map<Integer, Set<String>> inputMap) {
        Map<Integer, Set<String>> resultMap = new HashMap<>();

        for (Map.Entry<Integer, Set<String>> entry : inputMap.entrySet()) {
            if (entry.getValue().equals(minCount)) {
                resultMap.put(entry.getKey(), entry.getValue());
            }
        }

        return resultMap;
    }

    private Map<Integer, Set<String>> getIntersectionMap(
            List<Map.Entry<String, Set<String>>> queryMap,
            List<Map.Entry<String, Set<String>>> wordLemmasList) {

        Map<Integer, Set<String>> intersectionMap = new TreeMap<>();

        for (Map.Entry<String, Set<String>> queryEntry : queryMap) {
            Set<String> queryLemmas = queryEntry.getValue();

            for (int i = 0; i < wordLemmasList.size(); i++) {
                Set<String> intersection = new HashSet<>(queryLemmas);
                intersection.retainAll(wordLemmasList.get(i).getValue());

                if (!intersection.isEmpty()) {
                    intersectionMap.merge(i, intersection, (existing, newSet) -> {
                        existing.addAll(newSet);
                        return existing;
                    });
                }
            }
        }

        if (!intersectionMap.isEmpty() && intersectionMap.values().stream()
                .allMatch(value -> value.equals(intersectionMap.values().iterator().next()))) {
            return Map.of(intersectionMap.keySet().iterator().next(), intersectionMap.values().iterator().next());
        }

        return intersectionMap;
    }

    // получает значение с минимальным количеством
    private Set<String> getValueWithMinimalOccurrences(Map<Integer, Set<String>> inputMap) {
        Map<Set<String>, Integer> valueCountMap = new HashMap<>();

        for (Set<String> value : inputMap.values()) {
            valueCountMap.put(value, valueCountMap.getOrDefault(value, 0) + 1);
        }

        Set<String> result = null;
        int minCount = Integer.MAX_VALUE;

        for (Map.Entry<Set<String>, Integer> entry : valueCountMap.entrySet()) {
            if (entry.getValue() < minCount) {
                minCount = entry.getValue();
                result = entry.getKey();
            }
        }

        return result;
    }

    private String highlightKeywords(String snippet, String query) {
        List<Map.Entry<String, Set<String>>> queryMap = getCachedQueryLemmas(query);
        List<Map.Entry<String, Set<String>>> snippetMap = getWordLemmasList(snippet);
        Map<Integer, Set<String>> intersectionMap = getIntersectionMap(queryMap, snippetMap);

        if (intersectionMap.isEmpty()) {
            return snippet; // Ранний возврат, если пересечения отсутствуют
        }

        String[] words = snippet.split("\\s+");

        for (Map.Entry<Integer, Set<String>> entry : intersectionMap.entrySet()) {
            int wordIndex = entry.getKey();
            if (wordIndex < 0 || wordIndex >= words.length) {
                continue;
            }

            String originalWord = words[wordIndex];
            Set<String> intersectingWords = entry.getValue();

            if (shouldHighlight(originalWord, intersectingWords)) {
                words[wordIndex] = "<b>" + originalWord + "</b>";
            }
        }

        return String.join(" ", words);
    }

    private boolean shouldHighlight(String word, Set<String> intersectingLemmas) {
        Set<String> wordLemmas = queryUtil.extractLemmas(word.toLowerCase());
        for (String lemma : intersectingLemmas) {
            if (wordLemmas.contains(lemma)) {
                return true;
            }
        }
        return false;
    }
}
