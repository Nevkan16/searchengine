package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.constants.ErrorMessages;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.task.LinkProcessor;
import searchengine.utils.EntityTableUtil;
import searchengine.utils.HtmlLoader;
import searchengine.utils.Lemmatizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

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
    private final EntityTableUtil entityTableService;

    public void saveAndProcessPage(String url, Document document, SiteEntity siteEntity) throws Exception {
        String path = new URI(url).getPath();
        int statusCode = htmlLoader.getHttpStatusCode(url, fakeConfig);
        String content = document.html();

        if (LinkProcessor.isEmptyPage(content)) {
            log.info("Skipping empty page: {}", url);
            return;
        }

        PageEntity pageEntity = pageCRUDService.createPageIfNotExists(siteEntity, path, statusCode, content);
        log.info("Page saved to database: {}", path);

        processLemmasAndIndexes(pageEntity, siteEntity, content);
    }

    public void processPage(String url) {
        SiteEntity siteEntity = null;
        try {
            siteEntity = siteCRUDService.getSiteByUrl(getBaseUrl(url));
            if (siteEntity == null) {
                log.info("Сайт не найден. Попытка создать сайт...");
                siteEntity = siteCRUDService.createSiteIfNotExist(getBaseUrl(url));
                if (siteEntity == null) {
                    log.warn("Не удалось создать сайт для индексации: {}", url);
                    return;
                }
            }
            Optional<PageEntity> pageEntity = pageCRUDService.getPageByPath(getPath(url));
            pageEntity.ifPresent(page -> pageCRUDService.deletePageLemmaByPath(getPath(url)));
            entityTableService.resetAutoIncrementForAllTables();
            Document document = htmlLoader.fetchHtmlDocument(url, fakeConfig);
            if (document == null) {
                log.warn("Не удалось выполнить индексацию для страницы: {}", url);
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.PAGE_UNAVAILABLE);
                return;
            }
            saveAndProcessPage(url, document, siteEntity);
            siteCRUDService.updateSiteStatusAfterIndexing(getBaseUrl(url));
            log.info("Индексация страницы {} завершена успешно.", url);
        } catch (Exception e) {
            log.error("Ошибка при обработке страницы: {}", url, e);
            if (siteEntity != null) {
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.UNKNOWN_ERROR);
            }
        }
    }

    public String getBaseUrl(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(), uri.getHost(), null, null).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Некорректный URL: " + url, e);
        }
    }

    private String getPath(String url) {
        try {
            return new URI(url).getPath();
        } catch (Exception e) {
            log.error("Ошибка при извлечении path из URL: {}", url, e);
            return null;
        }
    }

    public void processLemmasAndIndexes(PageEntity pageEntity, SiteEntity siteEntity, String textContent) {
        Map<String, Integer> lemmasCount = lemmatizer.getLemmasCount(textContent);
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            LemmaEntity lemma = lemmaCRUDService.updateLemmaEntity(lemmaText, siteEntity);
            indexCRUDService.createIndex(pageEntity, lemma, count.floatValue());
        }
    }
}
