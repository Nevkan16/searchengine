package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.FakeConfig;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.HtmlLoader;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PageProcessor {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final Lemmatizer lemmatizer;
    private final HtmlLoader htmlLoader;
    private final FakeConfig fakeConfig;
    private final LemmaCRUDService lemmaCRUDService;
    private final IndexCRUDService indexCRUDService;
    private final PageCRUDService pageCRUDService;

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

        // Используем метод для создания страницы
        PageEntity page = pageCRUDService.createPageEntity(site, url, httpStatus, htmlContent);

        // Сохраняем страницу
        pageRepository.save(page);

        // Получение лемм и их количества
        Map<String, Integer> lemmasCount = lemmatizer.getLemmasCount(textContent);

        // Обработка лемм и связок "лемма-страница"
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            // Создать или обновить лемму
            LemmaEntity lemma = lemmaCRUDService.createOrUpdateLemma(lemmaText, site);

            // Создать индекс
            indexCRUDService.createIndex(page, lemma, count.floatValue());
        }

        // Обновление страницы с учетом связок
        pageRepository.save(page);
    }
}
