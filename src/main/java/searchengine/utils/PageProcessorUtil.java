package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import searchengine.config.FakeConfig;
import searchengine.constants.ErrorMessages;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.services.crud.IndexCRUDService;
import searchengine.services.crud.LemmaCRUDService;
import searchengine.services.crud.PageCRUDService;
import searchengine.services.crud.SiteCRUDService;
import searchengine.task.LinkProcessorTask;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PageProcessorUtil {

    private final LemmatizerUtil lemmatizerUtil;
    private final HtmlLoaderUtil htmlLoaderUtil;
    private final FakeConfig fakeConfig;
    private final LemmaCRUDService lemmaCRUDService;
    private final IndexCRUDService indexCRUDService;
    private final SiteCRUDService siteCRUDService;
    private final PageCRUDService pageCRUDService;
    private final EntityTableUtil entityTableService;

    public void saveAndProcessPage(String url, Document document, SiteEntity siteEntity) throws Exception {
        String path = new URI(url).getPath();
        int statusCode = htmlLoaderUtil.getHttpStatusCode(url, fakeConfig);
        String content = document.html();

        if (LinkProcessorTask.isEmptyPage(content)) {
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
            siteEntity = siteCRUDService.getSiteByUrl(HtmlLoaderUtil.getSchemeBaseUrl(url));
            if (siteEntity == null) {
                log.info("Сайт не найден. Попытка создать сайт...");
                siteEntity = siteCRUDService.createSiteIfNotExist(HtmlLoaderUtil.getSchemeBaseUrl(url));
                if (siteEntity == null) {
                    log.warn("Не удалось создать сайт для индексации: {}", url);
                    return;
                }
            }
            Optional<PageEntity> pageEntity = pageCRUDService.getPageByPath(HtmlLoaderUtil.getPath(url));
            pageEntity.ifPresent(page -> pageCRUDService.deletePageLemmaByPath(HtmlLoaderUtil.getPath(url)));
            entityTableService.resetAutoIncrementForAllTables();
            Document document = htmlLoaderUtil.fetchHtmlDocument(url, fakeConfig);
            if (document == null) {
                log.warn("Не удалось выполнить индексацию для страницы: {}", url);
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.PAGE_UNAVAILABLE);
                return;
            }
            saveAndProcessPage(url, document, siteEntity);
            siteCRUDService.updateSiteStatusAfterIndexing(HtmlLoaderUtil.getSchemeBaseUrl(url));
            log.info("Индексация страницы {} завершена успешно.", url);
        } catch (Exception e) {
            log.error("Ошибка при обработке страницы: {}", url, e);
            if (siteEntity != null) {
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.UNKNOWN_ERROR);
            }
        }
    }


    public void processLemmasAndIndexes(PageEntity pageEntity, SiteEntity siteEntity, String textContent) {
        Map<String, Integer> lemmasCount = lemmatizerUtil.getLemmasCount(textContent);
        for (Map.Entry<String, Integer> entry : lemmasCount.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            LemmaEntity lemma = lemmaCRUDService.updateLemmaEntity(lemmaText, siteEntity);
            indexCRUDService.createIndex(pageEntity, lemma, count.floatValue());
        }
    }
}
