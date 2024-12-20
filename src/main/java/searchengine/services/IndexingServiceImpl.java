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
    private final PageIndexingHelper pageIndexingHelper;
    private final PageProcessor pageProcessor;

    @Override
    public boolean startIndexing() {
        // Используем метод siteService для проверки текущего статуса индексации
        if (siteIndexingService.isIndexing()) {
            log.info("Индексация уже запущена.");
            return false;
        }

        try {
            log.info("Запуск процесса индексации...");
            siteDataExecutor.refreshAllSitesData();
            siteIndexingService.processSites(); // Запускаем процесс индексации
            return true;
        } catch (Exception e) {
            log.error("Ошибка при запуске индексации: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean stopIndexing() {
        // Проверяем, выполняется ли индексация через siteService
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

        try {
            // Получаем информацию о сайте
            SiteEntity site = pageIndexingHelper.getSiteByUrl(url);

            // Удаляем существующую страницу, если она есть
            pageIndexingHelper.deletePageIfExists(url);

            // Процессинг страницы
            pageProcessor.processPage(url, site.getId());

            log.info("Индексация страницы {} завершена успешно.", url);
            return true;

        } catch (Exception e) {
            log.error("Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
            return false;
        }
    }
}