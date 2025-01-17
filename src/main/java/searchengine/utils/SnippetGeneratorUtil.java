package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnippetGeneratorUtil {
    private final LemmatizerUtil lemmatizerUtil;
    private static final int SNIPPET_WINDOW = 160;
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

    public String generateSnippet(String content, List<String> queryLemmas) {
        String cleanedText = lemmatizerUtil.cleanHtml(content);

        cleanedText = cleanHtmlTags(cleanedText);

        List<Map.Entry<String, Set<String>>> queryMap = getWordLemmasList(queryLemmas.toString());
        List<Map.Entry<String, Set<String>>> wordLemmasList = getWordLemmasList(cleanedText);
        Map<Integer, Set<String>> intersectionMap = getIntersectionMap(queryMap, wordLemmasList);
        Set<String> minCount = getValueWithMinimalOccurrences(intersectionMap);
        Map<Integer, Set<String>> resultMap = countMinSize(minCount, intersectionMap);
        Map<Integer, Set<String>> rebuiltMap = rebuildResultMap(intersectionMap, resultMap);

        String snippet = extractTextFragments(rebuiltMap, cleanedText);

        return highlightKeywords(snippet, queryLemmas);
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
        StringBuilder snippets = new StringBuilder(); // Для объединения всех фрагментов

        // Разбиваем текст на слова
        String[] words = text.split("\\s+");

        // Если rebuildResultMap содержит только одно значение
        if (rebuildResultMap.size() == 1) {
            int index = rebuildResultMap.keySet().iterator().next(); // Получаем единственный ключ
            String snippet = buildSnippetAroundIndex(words, index);
            if (!snippet.isEmpty()) {
                snippets.append(snippet).append("\n"); // Добавляем фрагмент
            }
            return snippets.toString().trim();
        }

        // Обрабатываем rebuildResultMap с верхним и нижним ключами
        for (Integer key : rebuildResultMap.keySet()) {
            // Строим текст вокруг каждого ключа
            String snippet = buildSnippetAroundIndex(words, key);
            if (!snippet.isEmpty()) {
                snippets.append(snippet).append("\n"); // Добавляем фрагмент
            }
        }

        return snippets.toString().trim(); // Возвращаем объединённые фрагменты
    }

    // Метод для построения фрагмента вокруг индекса
    private String buildSnippetAroundIndex(String[] words, int index) {
        int start = index;
        int end = index;

        int charCount = calculateCharCount(words, start, end); // Подсчёт начального количества символов
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
            Integer boundaryKey = null; // Ключ для определения диапазона
            Set<Set<String>> currentUniqueValues = new HashSet<>();

            if (result.containsKey(currentKey)) {
                currentUniqueValues.add(result.get(currentKey));
            }

            while ((currentKey = getNextKey(currentKey, ascending)) != null) {
                Set<String> currentValue = result.get(currentKey);
                if (currentValue != null && currentUniqueValues.add(currentValue)) {
                    boundaryKey = currentKey; // Обновляем границу диапазона
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

    private String highlightKeywords(String snippet, List<String> queryLemmas) {
        Set<String> queryLemmaSet = queryLemmas.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        String[] words = snippet.split("\\s+");
        return Arrays.stream(words)
                .map(word -> {
                    String lemma = lemmatizerUtil.extractLemmasFromQuery(word).stream()
                            .findFirst()
                            .orElse("").toLowerCase();

                    if (queryLemmaSet.contains(lemma)) {
                        return "<b>" + word + "</b>";
                    }
                    return word;
                })
                .collect(Collectors.joining(" "));
    }
}
