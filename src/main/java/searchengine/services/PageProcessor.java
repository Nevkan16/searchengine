package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.FakeConfig;
import searchengine.model.*;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.HtmlLoader;
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
    private final HtmlLoader htmlLoader;
    private final FakeConfig fakeConfig;

    @Transactional
    public void processPage(String url, Long siteId) throws IOException {
        SiteEntity site = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));

        // Загрузка HTML-страницы с использованием HtmlLoader
        Document document = htmlLoader.fetchHtmlDocument(url, fakeConfig);
        if (document == null) {
            throw new IOException("Failed to load HTML document for URL: " + url);
        }

        // Получение HTTP-статуса
        int httpStatus;
        try {
            httpStatus = htmlLoader.getHttpStatusCode(url);
        } catch (Exception e) {
            throw new IOException("Failed to get HTTP status code for URL: " + url, e);
        }

        String htmlContent = document.html();
        String textContent = lemmatizer.cleanHtml(htmlContent);

        // Сохранение страницы в таблицу page
        PageEntity page = new PageEntity();
        page.setSite(site);
        page.setPath(url);
        page.setCode(httpStatus); // Устанавливаем HTTP-статус
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

            lemma.setFrequency(lemma.getFrequency() + 1);
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
