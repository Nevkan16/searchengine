package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.task.LinkProcessor;
import searchengine.utils.ConfigUtil;
import searchengine.utils.HtmlLoader;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataExecutor siteDataExecutor;
    private final SiteIndexingService siteIndexingService;
    private final PageProcessor pageProcessor;
    private final SiteCRUDService siteCRUDService;
    private final PageCRUDService pageCRUDService;
    private final ConfigUtil configUtil;
    private final FakeConfig fakeConfig;
    private final HtmlLoader htmlLoader;
    private final PageRepository pageRepository;

    @Override
    public boolean startIndexing() {
        if (siteIndexingService.isIndexing()) {
            log.info("Индексация уже запущена.");
            return false;
        }

        try {
            log.info("Запуск процесса индексации...");
            LinkProcessor.clearVisitedLinks();
            siteDataExecutor.refreshAllSitesData();
            siteIndexingService.processSites();
            return true;
        } catch (Exception e) {
            log.error("Ошибка при запуске индексации: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean stopIndexing() {
        if (!siteIndexingService.isIndexing()) {
            log.info("Индексация не запущена.");
            return false;
        }

        log.info("Остановка процесса индексации...");
        siteIndexingService.stopProcessing();
        return true;
    }

    @Override
    public boolean indexPage(String url) {
        log.info("Запуск индексации страницы: {}", url);

//        url = validateURL(url);

        if (url == null) {
            log.info("Индексация страницы остановлена: некорректный URL.");
            return false;
        }

        SiteEntity siteEntity;
        try {
            siteEntity = siteCRUDService.getSiteByUrl(getBaseUrl(url));

            // Если сайт не найден, создаём его
            if (siteEntity == null) {
                log.info("Сайт не найден. Попытка создать сайт...");
                siteEntity = siteCRUDService.createSiteIfNotExist(getBaseUrl(url));
                if (siteEntity == null) {
                    log.error("Не удалось создать сайт для индексации: {}", url);
                    return false;
                }
            }

            // Удаление страницы, если она уже существует
            Optional<PageEntity> pageEntity = pageCRUDService.getPageByPath(getPath(url));
            pageEntity.ifPresent(page -> pageCRUDService.deletePageLemmaByPath(getPath(url)));
            siteCRUDService.resetIncrement();

            // Загружаем HTML-документ и сохраняем/обрабатываем страницу
            Document document = htmlLoader.fetchHtmlDocument(url, fakeConfig);
            pageProcessor.saveAndProcessPage(url, document, siteEntity);
            log.info("Индексация страницы {} завершена успешно.", url);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            return false;
        }
    }

    private String getBaseUrl(String url) {
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


    private String validateURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.info("URL передан пустым");
            return null;
        }

        return configUtil.formatURL(url);
    }
}