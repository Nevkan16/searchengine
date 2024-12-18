package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class SiteDataExecutor {

    private final DataService dataService;
    private ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false); // Флаг выполнения
    private final SiteRepository siteRepository;

    public void refreshAllSitesData() {
        if (isRunning.get()) {
            System.out.println("Обновление уже запущено. Ожидайте завершения.");
            return;
        }

        System.out.println("Начало обновления данных для всех сайтов...");

        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            restartExecutor();
        }

        isRunning.set(true);

        List<Site> configuredSites = dataService.getAllSites();
        if (configuredSites.isEmpty()) {
            System.out.println("Список сайтов в конфигурации пуст. Удаление всех сайтов из базы данных...");
            dataService.deleteAllSites(); // Удалить все сайты, если список пуст
            dataService.resetIncrement();
            isRunning.set(false);
            return;
        }

        try {
            // Шаг 1: Удаление сайтов, которых нет в конфигурации
            dataService.deleteSitesNotInConfig(configuredSites);
            dataService.resetIncrement();

            // Шаг 2: Обновление данных сайтов (создание или обновление)
            configuredSites.forEach(site -> {
                Optional<SiteEntity> existingSiteOpt = siteRepository.findByUrl(site.getUrl());
                if (existingSiteOpt.isPresent()) {
                    long siteId = existingSiteOpt.get().getId();
                    executorService.submit(() -> dataService.updateSiteRecord(site, siteId));
                } else {
                    executorService.submit(() -> dataService.createSiteRecord(site));
                }
            });

            System.out.println("Обновление данных завершено.");
        } finally {
            shutdownExecutor();
            isRunning.set(false);
        }
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
        System.out.println("Пул потоков пересоздан.");
    }
}
