package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.task.LinkProcessor;
import searchengine.utils.ConfigUtil;

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

        url = validateURL(url);

        if (url == null) {
            log.info("Индексация страницы остановлена: некорректный URL.");
            return false;
        }

        try {
            handleExistingSite(url);
            if (siteCRUDService.isDatabaseEmpty()) {
                siteCRUDService.resetIncrement();
            }
            SiteEntity siteEntity = siteCRUDService.createSiteIfNotExist(url);
            if (siteEntity == null) {
                log.info("Индексация страницы остановлена.");
                return false;
            }
            pageCRUDService.deletePageIfExists(url);
            pageProcessor.processPage(url, siteEntity.getId());

            log.info("Индексация страницы {} завершена успешно.", url);
            return true;

        } catch (Exception e) {
            log.error("Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            return false;
        }
    }


    private String validateURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.info("URL передан пустым");
            return null;
        }

        return configUtil.formatURL(url);
    }

    private void handleExistingSite(String url) {
        SiteEntity siteEntity = siteCRUDService.getSiteByUrl(url);
        if (siteEntity != null) {
            log.info("Сайт с URL {} найден, удаляем его.", url);
            siteCRUDService.deleteSite(siteEntity.getId());
        }
    }

}