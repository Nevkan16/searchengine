package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class Lemmatizer {

    private static final Set<String> EXCLUDED_POS_TAGS = new HashSet<>(Arrays.asList(
            "СОЮЗ", "МЕЖД", "ПРЕДЛ", "ЧАСТ", "МС"));

    private final LuceneMorphology luceneMorphology;

    public Lemmatizer() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    private static String preprocessText(String text) {
        return text.replaceAll("[^а-яА-Я\\s]", "").toLowerCase();
    }

    private boolean isExcluded(List<String> morphInfo) {
        for (String info : morphInfo) {
            String[] parts = info.split("\\s+");
            if (parts.length > 1) {
                String posTag = parts[parts.length - 1];
                if (EXCLUDED_POS_TAGS.contains(posTag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> getLemmasForWord(String word) {
        List<String> morphInfo = luceneMorphology.getMorphInfo(word);

        if (morphInfo.isEmpty() || isExcluded(morphInfo)) {
            return Collections.emptyList();
        }

        List<String> lemmas = new ArrayList<>();
        for (String info : morphInfo) {
            String lemma = info.split("\\|")[0];
            lemmas.add(lemma);
        }
        return lemmas;
    }

    public Map<String, Integer> getLemmasCount(String text) {
        text = preprocessText(text);

        String[] words = text.split("\\s+");
        Map<String, Integer> lemmasCount = new HashMap<>();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            List<String> lemmas = getLemmasForWord(word);
            for (String lemma : lemmas) {
                lemmasCount.put(lemma, lemmasCount.getOrDefault(lemma, 0) + 1);
            }
        }
        return lemmasCount;
    }

    public String replaceWordsWithLemmas(String text) {
        text = preprocessText(text);

        StringBuilder result = new StringBuilder();
        String[] words = text.split("\\s+");

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            List<String> lemmas = getLemmasForWord(word);
            if (!lemmas.isEmpty()) {
                result.append(lemmas.get(0)).append(" "); // Берем первую лемму
            } else {
                result.append(word).append(" "); // Если лемма не найдена, оставляем оригинальное слово
            }
        }

        return result.toString().trim();
    }

    public HashMap<String, Integer> getLemmasWithCounts(String text) {
        HashMap<String, Integer> lemmasCount = new HashMap<>();

        // Предобработка текста
        text = preprocessText(text);

        // Разделение текста на слова
        String[] words = text.split("\\s+");

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            // Получаем леммы для текущего слова
            List<String> lemmas = getLemmasForWord(word);

            // Добавляем их в карту с учетом количества
            for (String lemma : lemmas) {
                lemmasCount.put(lemma, lemmasCount.getOrDefault(lemma, 0) + 1);
            }
        }
        return lemmasCount;
    }

    public void printLemmasWithCounts(HashMap<String, Integer> lemmasCount) {
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            System.out.println(entry.getKey() + " - " + entry.getValue());
        }
    }

//    public static void main(String[] args) throws IOException {
//        String text = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.";
//        Lemmatizer lemmatizer = new Lemmatizer();
//
//        HashMap<String, Integer> lemmas = lemmatizer.getLemmasWithCounts(text);
//        lemmatizer.printLemmasWithCounts(lemmas);
//        lemmas.forEach((lemma, count) -> System.out.println(lemma + " - " + count));
//
//    }
}
