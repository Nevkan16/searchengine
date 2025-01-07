package searchengine.services;

import lombok.Getter;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
@Getter
public class SiteIndexingService {

    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final FakeConfig fakeConfig;
    private final AtomicBoolean manuallyStopped = new AtomicBoolean(false);
    private final SiteCRUDService siteCRUDService;
    private final PageProcessor pageProcessor;
    private static final ConcurrentHashMap<String, AtomicBoolean> siteStopFlags = new ConcurrentHashMap<>();
    private static final AtomicBoolean stopProcessing = new AtomicBoolean(false);
    private final int maxDepth = 1;
    private static final int TASK_TIMEOUT_SECONDS = 10;

    public void processSites() {
        log.info("Запуск индексации страниц сайта..");
        if (isProcessing.get()) {
            log.info("Processing is already running!");
            return;
        }

        isProcessing.set(true);
        manuallyStopped.set(false);
        stopAllProcessing();
        resetStopProcessing();

        setupForkJoinPool();

        forkJoinPool.execute(() -> {
            try {
                List<String> sitesUrls = siteCRUDService.getSitesForIndexing();
                List<LinkTask> tasks = processEachSite(sitesUrls);

                waitForTasksCompletion(tasks);

                completeIndexing(sitesUrls);

            } catch (Exception e) {
                log.error("Error during indexing: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isProcessing.set(false);
            }
        });
    }

    private void setupForkJoinPool() {
        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdown();
        }
        forkJoinPool = new ForkJoinPool();
    }

    private List<LinkTask> processEachSite(List<String> sitesUrls) {
        List<LinkTask> tasks = new ArrayList<>();

        for (String siteUrl : sitesUrls) {
            try {
                siteStopFlags.put(siteUrl, new AtomicBoolean(false)); // Инициализация флага
                Document doc = Jsoup.connect(siteUrl).timeout(TASK_TIMEOUT_SECONDS * 1000).get();
                LinkTask linkTask = new LinkTask(
                        doc, siteUrl, 0, getMaxDepth(), fakeConfig, siteCRUDService, pageProcessor);
                tasks.add(linkTask);
                forkJoinPool.execute(linkTask);
            } catch (IOException e) {
                log.info("Error processing site: " + siteUrl);
            }
        }
        return tasks;
    }

    private boolean allSitesStopped() {
        return siteStopFlags.values().stream().allMatch(AtomicBoolean::get);
    }

    public static AtomicBoolean getStopFlagForSite(String siteUrl) {
        return siteStopFlags.get(siteUrl);
    }

    private void waitForTasksCompletion(List<LinkTask> tasks) {
        int canceledTask = 0;
        for (LinkTask task : tasks) {
            try {
                task.join();
            } catch (CancellationException ignored) {
                canceledTask++;
            }
        }
        if (canceledTask > 0) {
            log.info("Количество отмененных задача: " + canceledTask);
        }
    }

    private void completeIndexing(List<String> sitesUrls) {
        sitesUrls.forEach(siteUrl -> {
            siteCRUDService.updateSiteStatusToIndexed(siteUrl);
            siteStopFlags.remove(siteUrl);
        });

        if (!manuallyStopped.get()) {
            log.info("Indexing completed automatically.");
        } else {
            log.info("Indexing stopped by user.");
        }

        if (allSitesStopped()) {
           stopAllProcessing();
        }
    }

    public synchronized void stopProcessing() {
        if (!isProcessing.get()) {
            log.info("Процесс не запущен!");
            return;
        }

        if (manuallyStopped.compareAndSet(false, true)) {
            log.info("Stopping processing manually...");
            stopAllProcessing();

            List<LinkTask> tasks = new ArrayList<>();

            siteStopFlags.keySet().forEach(siteUrl -> {
                AtomicBoolean stopFlag = getStopFlagForSite(siteUrl);
                if (stopFlag != null) {
                    stopFlag.set(true);
                }
            });

            waitForTasksCompletion(tasks);

            if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
                forkJoinPool.shutdownNow();
            }

            isProcessing.set(false);
        }
    }

    public void stopAllProcessing() {
        stopProcessing.set(true);
    }

    public void resetStopProcessing() {
        stopProcessing.set(false);
    }

    public static boolean isStopProcessing() {
        return stopProcessing.get();
    }

    public boolean isIndexing() {
        return isProcessing.get();
    }
}