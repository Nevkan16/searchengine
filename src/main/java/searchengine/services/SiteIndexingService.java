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

    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final FakeConfig fakeConfig;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private boolean manuallyStopped = false;
    private final SiteCRUDService siteCRUDService;
    private final PageProcessor pageProcessor;

    public void processSites() {
        if (isProcessing.get()) {
            log.info("Processing is already running!");
            return;
        }

        isProcessing.set(true);
        manuallyStopped = false;
        LinkTask.stopProcessing();
        LinkTask.resetStopFlag();

        setupForkJoinPool();

        forkJoinPool.execute(() -> {
            try {
                List<String> sitesUrls = getSitesForIndexing();
                List<LinkTask> tasks = processEachSite(sitesUrls);

                waitForTasksCompletion(tasks);

                completeIndexing(sitesUrls);

            } catch (Exception e) {
                log.error("Error during indexing: " + e.getMessage());
            } finally {
                isProcessing.set(false);
            }
        });

        scheduleStopProcessing();
    }

    private void setupForkJoinPool() {
        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdown();
        }
        forkJoinPool = new ForkJoinPool();
    }

    private List<String> getSitesForIndexing() {
        return siteCRUDService.getSitesForIndexing();
    }

    private List<LinkTask> processEachSite(List<String> sitesUrls) {
        List<LinkTask> tasks = new ArrayList<>();

        for (String siteUrl : sitesUrls) {
            try {
                Document doc = Jsoup.connect(siteUrl).get();
                LinkTask linkTask = new LinkTask(
                        doc, siteUrl, 0, 2, fakeConfig, siteCRUDService, pageProcessor);
                tasks.add(linkTask);
                forkJoinPool.execute(linkTask);
            } catch (IOException e) {
                log.info("Error processing site: " + siteUrl);
            }
        }
        return tasks;
    }

    private void waitForTasksCompletion(List<LinkTask> tasks) {
        for (LinkTask task : tasks) {
            task.join();
        }
    }

    private void completeIndexing(List<String> sitesUrls) {
        if (!manuallyStopped) {
            log.info("Indexing completed automatically.");
            sitesUrls.forEach(siteCRUDService::updateSiteStatusToIndexed);
        } else {
            siteCRUDService.handleManualStop();
            log.info("Indexing stopped by user.");
        }
    }

    private void scheduleStopProcessing() {
        int stopDelaySeconds = 90;
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

        manuallyStopped = true;
        log.info("Stopping processing manually...");
        LinkTask.stopProcessing();

        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow();
        }

        isProcessing.set(false);
    }

    public boolean isIndexing() {
        return isProcessing.get();
    }
}