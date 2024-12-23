package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.task.LinkTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteIndexingService {

    private ForkJoinPool forkJoinPool = new ForkJoinPool(); // Создаём пул потоков
    private final AtomicBoolean isProcessing = new AtomicBoolean(false); // Состояние обработки
    private final FakeConfig fakeConfig;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean manuallyStopped = false; // Флаг для отслеживания ручной остановки
    private final SiteCRUDService siteCRUDService;
    private final PageCRUDService pageCRUDService;

    public void processSites() {
        if (isProcessing.get()) {
            log.info("Processing is already running!");
            return;
        }

        isProcessing.set(true); // Устанавливаем состояние "индексация запущена"
        manuallyStopped = false; // Сбрасываем флаг ручной остановки
        LinkTask.stopProcessing(); // Убедимся, что старые задачи остановлены
        LinkTask.resetStopFlag();  // Сбрасываем флаг остановки для новых задач

        // Настроим и запустим пул потоков
        setupForkJoinPool();

        forkJoinPool.execute(() -> {
            try {
                // Получаем и обрабатываем список URL сайтов
                List<String> sitesUrls = getSitesForIndexing();
                List<LinkTask> tasks = processEachSite(sitesUrls);

                // Ждем завершения всех задач
                waitForTasksCompletion(tasks);

                // Завершаем индексацию
                completeIndexing(sitesUrls);

            } catch (Exception e) {
                log.error("Error during indexing: " + e.getMessage());
            } finally {
                isProcessing.set(false); // Индексация завершена
            }
        });

        // Планируем автоматическую остановку индексации
        scheduleStopProcessing();
    }

    private void setupForkJoinPool() {
        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdown(); // Останавливаем старый пул потоков, если он не завершён
        }
        forkJoinPool = new ForkJoinPool(); // Создаём новый пул потоков
    }

    private List<String> getSitesForIndexing() {
        // Получаем список URL сайтов с статусом INDEXING
        return siteCRUDService.getSitesForIndexing();
    }

    private List<LinkTask> processEachSite(List<String> sitesUrls) {
        List<LinkTask> tasks = new ArrayList<>();

        // Обрабатываем каждый URL
        for (String siteUrl : sitesUrls) {
            try {
                Document doc = Jsoup.connect(siteUrl).get();
                LinkTask linkTask = new LinkTask(doc, siteUrl, 0, 2, fakeConfig, siteCRUDService, pageCRUDService);
                tasks.add(linkTask);
                forkJoinPool.execute(linkTask);
            } catch (IOException e) {
                log.info("Error processing site: " + siteUrl);
            }
        }
        return tasks;
    }

    private void waitForTasksCompletion(List<LinkTask> tasks) {
        // Ждем завершения всех задач
        for (LinkTask task : tasks) {
            task.join();
        }
    }

    private void completeIndexing(List<String> sitesUrls) {
        if (!manuallyStopped) {
            // Индексация завершена автоматически
            log.info("Indexing completed automatically.");
            // Обновляем статус всех сайтов
            sitesUrls.forEach(siteCRUDService::updateSiteStatusToIndexed);
        } else {
            siteCRUDService.handleManualStop();
            log.info("Indexing stopped by user.");
        }
    }

    private void scheduleStopProcessing() {
        int stopDelaySeconds = 15; // Время задержки в секундах
        scheduler.schedule(() -> {
            if (isProcessing.get() && !manuallyStopped) {
                log.info("Automatically stopping processing after " + stopDelaySeconds + " seconds...");
                LinkTask.stopProcessing();
                if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
                    forkJoinPool.shutdownNow();
                }
                isProcessing.set(false);
            }
        }, stopDelaySeconds, TimeUnit.SECONDS);
    }

    public synchronized void stopProcessing() {
        if (!isProcessing.get()) {
            log.info("Processing is not running!");
            return;
        }

        manuallyStopped = true; // Отмечаем, что остановка была вызвана вручную
        log.info("Stopping processing manually...");
        LinkTask.stopProcessing();

        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow(); // Останавливаем пул потоков
        }

        isProcessing.set(false); // Устанавливаем состояние "индексация остановлена"
    }

    public boolean isIndexing() {
        return isProcessing.get();
    }
}