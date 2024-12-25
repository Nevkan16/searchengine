package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.task.LinkProcessor;
import searchengine.utils.ConfigUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataExecutor siteDataExecutor;
    private final SiteIndexingService siteIndexingService;
    private final PageProcessor pageProcessor;
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

        url = configUtil.validateURL(url);

        if (url == null) {
            log.info("Индексация страницы остановлена: некорректный URL.");
            return false;
        }

        try {
            pageProcessor.processPage(url);
            log.info("Индексация страницы {} завершена успешно.", url);
            return true;
        } catch (Exception e) {
            log.info("Ошибка при индексации страницы {}: {}", url, e.getMessage());
            return false;
        }
    }
}