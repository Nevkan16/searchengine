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
            siteEntity = siteCRUDService.getSiteByUrl(HtmlLoader.getSchemeBaseUrl(url));
            if (siteEntity == null) {
                log.info("Сайт не найден. Попытка создать сайт...");
                siteEntity = siteCRUDService.createSiteIfNotExist(HtmlLoader.getSchemeBaseUrl(url));
                if (siteEntity == null) {
                    log.warn("Не удалось создать сайт для индексации: {}", url);
                    return;
                }
            }
            Optional<PageEntity> pageEntity = pageCRUDService.getPageByPath(HtmlLoader.getPath(url));
            pageEntity.ifPresent(page -> pageCRUDService.deletePageLemmaByPath(HtmlLoader.getPath(url)));
            entityTableService.resetAutoIncrementForAllTables();
            Document document = htmlLoader.fetchHtmlDocument(url, fakeConfig);
            if (document == null) {
                log.warn("Не удалось выполнить индексацию для страницы: {}", url);
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.PAGE_UNAVAILABLE);
                return;
            }
            saveAndProcessPage(url, document, siteEntity);
            siteCRUDService.updateSiteStatusAfterIndexing(HtmlLoader.getSchemeBaseUrl(url));
            log.info("Индексация страницы {} завершена успешно.", url);
        } catch (Exception e) {
            log.error("Ошибка при обработке страницы: {}", url, e);
            if (siteEntity != null) {
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.UNKNOWN_ERROR);
            }
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
