package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataExecutor siteDataExecutor;
    private final SiteIndexingService siteIndexingService;
    private final PageProcessor pageProcessor;
    private final SiteCRUDService siteCRUDService;
    private final PageCRUDService pageCRUDService;

    @Override
    public boolean startIndexing() {
        if (siteIndexingService.isIndexing()) {
            log.info("Индексация уже запущена.");
            return false;
        }

        try {
            log.info("Запуск процесса индексации...");
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

        if (!isUrlValid(url)) {
            return false;
        }

        try {
            handleExistingSite(url);
            if (siteCRUDService.isDatabaseEmpty()) {
                siteCRUDService.resetIncrement();
            }
            SiteEntity siteEntity = siteCRUDService.createSiteIfNotExist(url);
            pageCRUDService.deletePageIfExists(url);
            pageProcessor.processPage(url, siteEntity.getId());

            log.info("Индексация страницы {} завершена успешно.", url);
            return true;

        } catch (Exception e) {
            log.error("Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            return false;
        }
    }

    private boolean isUrlValid(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.error("URL не может быть пустым или null.");
            return false;
        }
        return true;
    }

    private void handleExistingSite(String url) {
        SiteEntity siteEntity = siteCRUDService.getSiteByUrl(url);
        if (siteEntity != null) {
            log.info("Сайт с URL {} найден, удаляем его.", url);
            siteCRUDService.deleteSite(siteEntity.getId());
        }
    }

}