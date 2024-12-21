package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteDataExecutor {

    private final SiteCRUDService dataService;
    private ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false); // Флаг выполнения
    private final SiteRepository siteRepository;
    private final PageCRUDService pageCRUDService;
//    private final AutoIncrementService autoIncrementService;

    public void refreshAllSitesData() {
        if (isRunning.get()) {
            log.info("Обновление уже запущено. Ожидайте завершения.");
            return;
        }

        log.info("Начало обновления данных для всех сайтов...");

        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            restartExecutor();
        }

        isRunning.set(true);

        List<Site> configuredSites = dataService.getAllSites();
        if (configuredSites.isEmpty()) {
            handleEmptyConfiguredSites();
            return;
        }

        try {
            deleteSitesNotInConfig(configuredSites);
            createOrUpdateSites(configuredSites);
            log.info("Обновление данных завершено.");
        } finally {
            shutdownExecutor();
            isRunning.set(false);
        }
    }

    private void handleEmptyConfiguredSites() {
        log.info("Список сайтов в конфигурации пуст. Удаление всех сайтов из базы данных...");
        dataService.deleteAllSites(); // Удалить все сайты, если список пуст
        dataService.resetIncrement();
        isRunning.set(false);
    }

    private void deleteSitesNotInConfig(List<Site> configuredSites) {
        log.info("Удаление сайтов, которых нет в конфигурации...");
        dataService.deleteSitesNotInConfig(configuredSites);
        pageCRUDService.deleteAllPages();
        dataService.resetIncrement();
    }

    private void createOrUpdateSites(List<Site> configuredSites) {
        log.info("Создание или обновление сайтов...");
        configuredSites.forEach(site -> executorService.submit(() -> {
            SiteEntity existingSite = siteRepository.findByUrl(site.getUrl())
                    .orElse(null);
            if (existingSite != null) {
                // Если сайт существует, обновляем его
                log.info("Обновление сайта: " + site.getUrl());
                dataService.updateSite(existingSite.getId(), site); // Используем метод updateSite
            } else {
                // Если сайт не существует, создаём новый
                log.info("Создание нового сайта: " + site.getUrl());
                dataService.createSite(site); // Используем метод createSite
            }
        }));
    }

    private void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void restartExecutor() {
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        log.info("Пул потоков пересоздан.");
    }
}
