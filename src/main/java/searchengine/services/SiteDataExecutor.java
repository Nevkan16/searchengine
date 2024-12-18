package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;

import java.util.List;
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

        List<Site> allSites = dataService.getAllSites();
        if (allSites.isEmpty()) {
            System.out.println("Список сайтов пуст. Обновление завершено.");
            isRunning.set(false);
            return;
        }

        try {
            // Шаг 1: Удаление всех сайтов из базы данных
            allSites.forEach(site -> dataService.deleteSiteByUrl(site.getUrl()));

            // Шаг 2: Сброс автоинкремента
            dataService.resetIncrement();

            // Шаг 3: Создание записей в базе данных в многопоточном режиме
            allSites.forEach(site -> executorService.submit(() -> dataService.createSiteRecord(site)));

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
