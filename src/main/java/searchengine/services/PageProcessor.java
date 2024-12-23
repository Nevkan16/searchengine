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
import searchengine.utils.HtmlLoader;
import searchengine.utils.Lemmatizer;

import java.io.IOException;
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

    @Transactional
    public void processPage(String url, Document document) throws IOException {
        SiteEntity site = siteCRUDService.getSiteByUrl(url);
        int httpStatus = getHttpStatus(url);
        String htmlContent = document.html();
        String textContent = lemmatizer.cleanHtml(htmlContent);

        pageCRUDService.deletePageIfExists(url);

        PageEntity page = pageCRUDService.createPageIfNotExists(site, url, httpStatus, htmlContent);
        processLemmasAndIndexes(page, site, textContent);
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

    private void processLemmasAndIndexes(PageEntity pageEntity, SiteEntity siteEntity, String textContent) {
        Map<String, Integer> lemmasCount = lemmatizer.getLemmasCount(textContent);
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            LemmaEntity lemma = lemmaCRUDService.updateLemmaEntity(lemmaText, siteEntity, pageEntity);
            indexCRUDService.createIndex(pageEntity, lemma, count.floatValue());
        }
    }
}
