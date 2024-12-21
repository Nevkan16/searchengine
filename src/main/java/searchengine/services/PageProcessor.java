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
import java.util.Optional;

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
        SiteEntity site = getSiteById(siteId);
        Document document = loadHtmlDocument(url);
        int httpStatus = getHttpStatus(url);
        String htmlContent = document.html();
        String textContent = lemmatizer.cleanHtml(htmlContent);

        removeExistingPageIfPresent(site, url);

        PageEntity page = createAndSavePage(site, url, httpStatus, htmlContent);
        processLemmasAndIndexes(page, site, textContent);
    }

    private SiteEntity getSiteById(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
    }

    private Document loadHtmlDocument(String url) throws IOException {
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

    private void removeExistingPageIfPresent(SiteEntity site, String url) {
        Optional<PageEntity> existingPageOptional = pageRepository.findBySiteAndPath(site, url);
        if (existingPageOptional.isPresent()) {
            PageEntity existingPage = existingPageOptional.get();
            existingPage.getIndexes().forEach(indexCRUDService::deleteIndex);
            pageRepository.delete(existingPage);
        }
    }

    private PageEntity createAndSavePage(SiteEntity site, String url, int httpStatus, String htmlContent) {
        PageEntity page = pageCRUDService.createPageEntity(site, url, httpStatus, htmlContent);
        return pageRepository.save(page);
    }

    private void processLemmasAndIndexes(PageEntity page, SiteEntity site, String textContent) {
        Map<String, Integer> lemmasCount = lemmatizer.getLemmasCount(textContent);
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            LemmaEntity lemma = lemmaCRUDService.updateLemmaEntity(lemmaText, site);
            indexCRUDService.createIndex(page, lemma, count.floatValue());
        }
    }
}
