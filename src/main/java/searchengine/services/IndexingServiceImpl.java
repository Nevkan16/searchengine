package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.SiteEntity;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataExecutor siteDataExecutor;
    private final SiteIndexingService siteIndexingService;
    private final PageIndexingHelper pageIndexingHelper;
    private final PageProcessor pageProcessor;
    private final SiteDataService siteDataService;
//    private final AutoIncrementService autoIncrementService;

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

        // Проверка на пустой или некорректный URL
        if (url == null || url.trim().isEmpty()) {
            log.error("URL не может быть пустым или null.");
            return false;
        }

        try {
            // Получаем информацию о сайте
            SiteEntity site = pageIndexingHelper.getSiteByUrl(url);

            // Если сайт не найден, создаём новый
            if (site == null) {
                log.info("Сайт с URL {} не найден в базе, создаём новый.", url);

                // Получение имени сайта из конфигурации
                String siteName = pageIndexingHelper.getSiteNameFromConfig(url);
                if (siteName == null) {
                    log.error("Не удалось найти имя для сайта с URL: {}", url);
                    return false;
                }

                // Создаем новый сайт с данными из конфигурации
                Site newSite = new Site();
                newSite.setUrl(url);
                newSite.setName(siteName);
                siteDataService.createSite(newSite);

                // После создания нового сайта получаем его из базы
                site = pageIndexingHelper.getSiteByUrl(url);
            }

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