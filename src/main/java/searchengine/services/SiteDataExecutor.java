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

    private final SiteCRUDService siteCRUDService;
    private ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false); // Флаг выполнения
    private final SiteRepository siteRepository;

    public void refreshAllSitesData() {
        siteCRUDService.deleteAllSites();
        siteCRUDService.resetIncrement();
        if (isRunning.get()) {
            log.info("Обновление уже запущено. Ожидайте завершения.");
            return;
        }

        log.info("Начало обновления данных для всех сайтов...");

        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            restartExecutor();
        }

        isRunning.set(true);

        List<Site> configuredSites = siteCRUDService.getAllSites();

        try {
            createOrUpdateSites(configuredSites);
            log.info("Обновление данных завершено.");
        } finally {
            shutdownExecutor();
            isRunning.set(false);
        }
    }

    private void createOrUpdateSites(List<Site> configuredSites) {
        log.info("Создание или обновление сайтов...");
        configuredSites.forEach(site -> executorService.submit(() -> {
            SiteEntity existingSite = siteRepository.findByUrl(site.getUrl())
                    .orElse(null);
            if (existingSite != null) {
                // Если сайт существует, обновляем его
                log.info("Обновление сайта: " + site.getUrl());
                siteCRUDService.updateSite(existingSite.getId(), site); // Используем метод updateSite
            } else {
                // Если сайт не существует, создаём новый
                log.info("Создание нового сайта: " + site.getUrl());
                siteCRUDService.createSite(site); // Используем метод createSite
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
