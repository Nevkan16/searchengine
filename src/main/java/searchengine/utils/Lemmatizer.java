package searchengine.utils;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
@Component
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

    public String cleanHtml(String html) {
        return Jsoup.parse(html).text();
    }

    public Set<String> extractLemmasFromQuery(String query) {
        String processQuery = preprocessText(query);
        String[] words = processQuery.split("\\s+");
        Set<String> uniqueLemmas = new HashSet<>();

        for (String word : words) {
            if (!word.isEmpty()) {
                List<String> lemmas = getLemmasForWord(word);
                uniqueLemmas.addAll(lemmas);
            }
        }
        return uniqueLemmas;
    }
}
