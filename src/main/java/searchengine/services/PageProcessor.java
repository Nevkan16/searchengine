package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.FakeConfig;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.task.LinkProcessor;
import searchengine.utils.HtmlLoader;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
@Slf4j
@Service
@RequiredArgsConstructor
public class PageProcessor {

    private final Lemmatizer lemmatizer;
    private final HtmlLoader htmlLoader;
    private final FakeConfig fakeConfig;
    private final LemmaCRUDService lemmaCRUDService;
    private final IndexCRUDService indexCRUDService;
    private final SiteCRUDService siteCRUDService;
    private final PageCRUDService pageCRUDService;

    // Общий метод для сохранения страницы и обработки лемм
    public void saveAndProcessPage(String url, Document document, SiteEntity siteEntity) throws Exception {
        String path = new URI(url).getPath();
        int statusCode = getHttpStatus(url);
        String content = document.html();

        if (LinkProcessor.isEmptyPage(content)) {
            log.info("Skipping empty page: {}", url);
            return;
        }

        // Сохраняем страницу в базе данных через сервис
        PageEntity pageEntity = pageCRUDService.createPageIfNotExists(siteEntity, path, statusCode, content);
        log.info("Page saved to database: {}", path);

        // Обрабатываем леммы и индексы
        processLemmasAndIndexes(pageEntity, siteEntity, content);
    }

    // Обновляем метод processPage
    @Transactional
    public void processPage(String url, Document document) throws Exception {
        SiteEntity site = siteCRUDService.getSiteByUrl(url);
        saveAndProcessPage(url, document, site); // Используем общий метод для сохранения страницы и обработки лемм
    }


    Document loadHtmlDocument(String url) throws IOException {
        Document document = htmlLoader.fetchHtmlDocument(url, fakeConfig);
        if (document == null) {
            throw new IOException("Failed to load HTML document for URL: " + url);
        }
        return document;
    }

    private int getHttpStatus(String url) throws IOException {
        try {
            return htmlLoader.getHttpStatusCode(url);
        } catch (Exception e) {
            throw new IOException("Failed to get HTTP status code for URL: " + url, e);
        }
    }

    public void processLemmasAndIndexes(PageEntity pageEntity, SiteEntity siteEntity, String textContent) {
        Map<String, Integer> lemmasCount = lemmatizer.getLemmasCount(textContent);
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            LemmaEntity lemma = lemmaCRUDService.updateLemmaEntity(lemmaText, siteEntity, pageEntity);
            indexCRUDService.createIndex(pageEntity, lemma, count.floatValue());
        }
    }
}
