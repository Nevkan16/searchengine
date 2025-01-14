package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnippetGenerator {
    private final Lemmatizer lemmatizer;
    private static final int SNIPPET_WINDOW = 200; // Максимальная длина сниппета в символах

    public String generateSnippet(String content, List<String> queryLemmas) {
        String cleanedText = lemmatizer.cleanHtml(content);

        cleanedText = cleanHtmlTags(cleanedText);

        String snippet = findSnippetCenteredOnFirstLemma(cleanedText, queryLemmas);

        return highlightKeywords(snippet, queryLemmas);
    }

    private String cleanHtmlTags(String input) {
        return input.replaceAll("<[^>]*>", "");
    }

    private String findSnippetCenteredOnFirstLemma(String text, List<String> queryLemmas) {
        if (queryLemmas.isEmpty()) {
            return "";
        }
        String firstLemma = queryLemmas.get(0);
        int index = text.toLowerCase().indexOf(firstLemma.toLowerCase());
        if (index != -1) {

            int rawStart = Math.max(0, index - SNIPPET_WINDOW / 2);
            int rawEnd = Math.min(text.length(), rawStart + SNIPPET_WINDOW);

            int sentenceStart = adjustToWordBoundary(text, rawStart, true);
            int sentenceEnd = adjustToWordBoundary(text, rawEnd, false);

            String snippet = text.substring(sentenceStart, sentenceEnd).trim();

            boolean isStartTrimmed = sentenceStart > 0;
            boolean isEndTrimmed = sentenceEnd < text.length();

            if (isStartTrimmed && isEndTrimmed) {
                snippet = "..." + snippet + "...";
            } else if (isStartTrimmed) {
                snippet = "..." + snippet;
            } else if (isEndTrimmed) {
                snippet = snippet + "...";
            }

            return snippet;
        }
        return "";
    }

    private String highlightKeywords(String snippet, List<String> queryLemmas) {
        Set<String> queryLemmaSet = queryLemmas.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        String[] words = snippet.split("\\s+");
        return Arrays.stream(words)
                .map(word -> {
                    String lemma = lemmatizer.extractLemmasFromQuery(word).stream()
                            .findFirst()
                            .orElse("").toLowerCase();

                    if (queryLemmaSet.contains(lemma)) {
                        return "<b>" + word + "</b>";
                    }
                    return word;
                })
                .collect(Collectors.joining(" "));
    }

    private int adjustToWordBoundary(String text, int index, boolean isStart) {
        if (isStart) {
            while (index > 0 && !Character.isWhitespace(text.charAt(index - 1))) {
                index--;
            }
        } else {
            while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
        return index;
    }
}
