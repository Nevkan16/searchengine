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
        if (isRunning.get()) { // Проверка: если уже запущен, выходим
            System.out.println("Обновление уже запущено. Ожидайте завершения.");
            return;
        }

        System.out.println("Обновление данных для всех сайтов...");

        if (executorService == null || executorService.isShutdown() || executorService.isTerminated()) {
            restartExecutor();
        }

        isRunning.set(true); // Устанавливаем флаг выполнения

        List<Site> allSites = dataService.getAllSites();
        if (allSites.isEmpty()) {
            System.out.println("Список сайтов пуст. Обновление завершено.");
            isRunning.set(false); // Сбрасываем флаг
            return;
        }

        try {
            allSites.forEach(site -> executorService.submit(() -> processSite(site)));
        } finally {
            shutdownExecutor();
            isRunning.set(false); // Сбрасываем флаг выполнения
        }

        System.out.println("Обновление данных завершено.");
    }

    private void processSite(Site site) {
        String siteUrl = site.getUrl();
        System.out.println("Обработка сайта: " + siteUrl);

        synchronized (this) {
            dataService.deleteSiteByUrl(siteUrl);
            dataService.createSiteRecord(site);
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
