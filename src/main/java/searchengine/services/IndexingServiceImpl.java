package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
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
            SiteEntity site = siteCRUDService.getSiteByUrl(url);

            // Если сайт не найден, создаём новый
            if (site == null) {
                log.info("Сайт с URL {} не найден в базе, обрабатываем...", url);

                // Получение имени сайта из конфигурации
                String siteName = configUtil.getSiteNameFromConfig(url);
                if (siteName == null) {
                    log.error("Сайт не найден в файле конфигурации.: {}", url);
                    return false;
                }

                // Создаем новый сайт с данными из конфигурации
                Site newSite = new Site();
                newSite.setUrl(url);
                newSite.setName(siteName);
                siteCRUDService.createSite(newSite);

                // После создания нового сайта получаем его из базы
                site = siteCRUDService.getSiteByUrl(url);
            }

            // Удаляем существующую страницу, если она есть
            pageCRUDService.deletePageIfExists(url);

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