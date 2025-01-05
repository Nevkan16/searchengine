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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteIndexingService {

    private ForkJoinPool forkJoinPool = new ForkJoinPool();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final FakeConfig fakeConfig;
    private final AtomicBoolean manuallyStopped = new AtomicBoolean(false);
    private final SiteCRUDService siteCRUDService;
    private final PageProcessor pageProcessor;

    public void processSites() {
        if (isProcessing.get()) {
            log.info("Processing is already running!");
            return;
        }

        isProcessing.set(true);
        manuallyStopped.set(false);
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
        sitesUrls.forEach(siteCRUDService::updateSiteStatusToIndexed);
        if (!manuallyStopped.get()) {
            log.info("Indexing completed automatically.");
        } else {
            log.info("Indexing stopped by user.");
        }
    }

    public synchronized void stopProcessing() {
        if (!isProcessing.get()) {
            log.info("Процесс не запущен!");
            return;
        }

        if (manuallyStopped.compareAndSet(false, true)) {
            log.info("Stopping processing manually...");
            LinkTask.stopProcessing();

            if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
                forkJoinPool.shutdownNow();
            }

            isProcessing.set(false);
        }
    }

    public boolean isIndexing() {
        return isProcessing.get();
    }
}