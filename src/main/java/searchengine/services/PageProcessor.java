package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PageProcessor {

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final SiteRepository siteRepository;
    private final Lemmatizer lemmatizer;

    @Transactional
    public void processPage(String url, Long siteId) throws IOException {
        SiteEntity site = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));

        // Загрузка HTML-страницы
        Document document = Jsoup.connect(url).get();
        String htmlContent = document.html();
        String textContent = lemmatizer.cleanHtml(htmlContent);

        // Сохранение страницы в таблицу page
        PageEntity page = new PageEntity();
        page.setSite(site);
        page.setPath(url);
        page.setCode(200); // HTTP статус страницы
        page.setContent(htmlContent);
        pageRepository.save(page);

        // Получение лемм и их количества
        Map<String, Integer> lemmasCount = lemmatizer.getLemmasCount(textContent);

        // Обработка лемм и связок "лемма-страница"
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            // Обновление таблицы lemma
            LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                    .orElseGet(() -> {
                        LemmaEntity newLemma = new LemmaEntity();
                        newLemma.setLemma(lemmaText);
                        newLemma.setFrequency(0);
                        newLemma.setSite(site);
                        return newLemma;
                    });

            if (lemma.getId() != null) {
                lemma.setFrequency(lemma.getFrequency() + 1);
            } else {
                lemma.setFrequency(1);
            }
            lemmaRepository.save(lemma);

            // Добавление записи в таблицу index
            IndexEntity index = new IndexEntity();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count.floatValue());
            page.getIndexes().add(index);
        }

        // Обновление страницы с учетом связок
        pageRepository.save(page);
    }
}

