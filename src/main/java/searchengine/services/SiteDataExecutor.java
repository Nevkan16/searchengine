package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;
import searchengine.utils.EntityTableService;

import javax.persistence.EntityManager;
import java.util.ArrayList;
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
    private final SiteRepository siteRepository;
    private final EntityTableService entityTableService;
    private final EntityManager entityManager;

    private ExecutorService executorService;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public void refreshAllSitesData() {
        if (isRunning.get()) {
            log.info("Обновление уже запущено. Ожидайте завершения.");
            return;
        }

        log.info("Начало обновления данных для всех сайтов...");

        // Пересоздать пул, если он завершён
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            restartExecutor();
        }

        isRunning.set(true);

        try {
            List<Site> configuredSites = siteCRUDService.getAllSites();

            // Удаление старых сайтов
            deleteSitesInParallel(configuredSites);

            // Получение названий таблиц
            List<String> tableNames = entityTableService.getEntityTableNames();

            // Сброс автоинкремента для всех таблиц
            resetAutoIncrementForAllTables(tableNames);

            // Создание или обновление сайтов
            createOrUpdateSites(configuredSites);

            log.info("Обновление данных завершено.");
        } finally {
            shutdownExecutor();
            isRunning.set(false);
        }
    }

    private void deleteSitesInParallel(List<Site> configuredSites) {
        log.info("Начало удаления старых сайтов...");
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            restartExecutor();
        }

        List<SiteEntity> sitesToDelete = new ArrayList<>();
        configuredSites.forEach(site -> executorService.submit(() -> {
            siteRepository.findByUrl(site.getUrl()).ifPresent(siteEntity -> {
                log.info("Сайт найден в базе данных: {}", site.getUrl());
                synchronized (sitesToDelete) {
                    sitesToDelete.add(siteEntity);
                }
            });
        }));

        shutdownExecutor();
        log.info("Количество сайтов, найденных для удаления: {}", sitesToDelete.size());

        if (!sitesToDelete.isEmpty()) {
            deleteSites(sitesToDelete);
        } else {
            log.info("Нет сайтов для удаления.");
        }
    }

    private void deleteSites(List<SiteEntity> sitesToDelete) {
        log.info("Начало удаления сайтов...");
        try {
            for (SiteEntity site : sitesToDelete) {
                log.info("Удаление сайта: {}", site.getUrl());
            }
            siteRepository.deleteAll(sitesToDelete);
            log.info("Удаление завершено. Всего удалено сайтов: {}", sitesToDelete.size());
        } catch (Exception e) {
            log.error("Ошибка при удалении сайтов", e);
        }
    }

    private void resetAutoIncrementForAllTables(List<String> tableNames) {
        log.info("Сброс автоинкремента для всех таблиц...");
        tableNames.forEach(siteCRUDService::resetAutoIncrement);
        log.info("Сброс автоинкремента завершён.");
    }

    private void createOrUpdateSites(List<Site> configuredSites) {
        log.info("Создание или обновление сайтов...");
        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            restartExecutor();
        }

        configuredSites.forEach(site -> executorService.submit(() -> {
            SiteEntity existingSite = siteRepository.findByUrl(site.getUrl())
                    .orElse(null);
            if (existingSite != null) {
                log.info("Обновление сайта: {}", site.getUrl());
                siteCRUDService.updateSite(existingSite.getId(), site);
            } else {
                siteCRUDService.createSite(site);
            }
        }));
    }

    private void shutdownExecutor() {
        log.info("Ожидание завершения всех потоков...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Не все потоки завершились вовремя. Принудительное завершение...");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Ошибка при завершении потоков", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Все потоки завершены.");
    }

    private void restartExecutor() {
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        log.info("Пул потоков пересоздан.");
    }
}
