package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;
import searchengine.task.LinkTask;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SitesList sitesList;
    private ForkJoinPool forkJoinPool = new ForkJoinPool(); // Создаём пул потоков
    private final AtomicBoolean isProcessing = new AtomicBoolean(false); // Состояние обработки
    private final FakeConfig fakeConfig;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private boolean manuallyStopped = false; // Флаг для отслеживания ручной остановки
    private final DataService dataService;
    private final PageDataService pageDataService;

    public void processSites() {
        if (isProcessing.get()) {
            System.out.println("Processing is already running!");
            return;
        }
        isProcessing.set(true); // Устанавливаем состояние "индексация запущена"
        manuallyStopped = false; // Сбрасываем флаг ручной остановки
        LinkTask.stopProcessing(); // Убедимся, что старые задачи остановлены
        LinkTask.resetStopFlag();  // Сбрасываем флаг остановки для новых задач

        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdown(); // Останавливаем старый пул потоков, если он не завершён
        }
        forkJoinPool = new ForkJoinPool(); // Создаём новый пул потоков

        forkJoinPool.execute(() -> {
            System.out.println("Indexing started...");

            try {
                // Получаем список URL сайтов с статусом INDEXING
                List<String> sitesUrls = dataService.getSitesForIndexing();
                List<LinkTask> tasks = new ArrayList<>();

                // Обрабатываем каждый URL
                for (String siteUrl : sitesUrls) {
                    try {
                        Document doc = Jsoup.connect(siteUrl).get();
                        LinkTask linkTask = new LinkTask(
                                doc, siteUrl, 0, 2, fakeConfig, pageDataService);
                        tasks.add(linkTask);
                        forkJoinPool.execute(linkTask);
                    } catch (IOException e) {
                        System.out.println("Error processing site: " + siteUrl);
                    }
                }

                // Ждем завершения всех задач
                for (LinkTask task : tasks) {
                    task.join();
                }

                if (!manuallyStopped) {
                    // Индексация завершена автоматически
                    System.out.println("Indexing completed automatically.");
                    // Обновляем статус всех сайтов
                    sitesUrls.forEach(dataService::updateSiteStatusToIndexed);
                } else {
                    dataService.handleManualStop();
                    System.out.println("Indexing stopped by user.");
                }
            } catch (Exception e) {
                System.out.println("Error during indexing: " + e.getMessage());
            } finally {
                isProcessing.set(false); // Индексация завершена
            }
        });

        scheduleStopProcessing();
    }



    private void scheduleStopProcessing() {
        int stopDelaySeconds = 100; // Время задержки в секундах
        scheduler.schedule(() -> {
            if (isProcessing.get() && !manuallyStopped) {
                System.out.println("Automatically stopping processing after " + stopDelaySeconds + " seconds...");
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
            System.out.println("Processing is not running!");
            return;
        }

        manuallyStopped = true; // Отмечаем, что остановка была вызвана вручную
        System.out.println("Stopping processing manually...");
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
