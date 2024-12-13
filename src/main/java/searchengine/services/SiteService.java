package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.task.LinkExtractor;

import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SitesList sitesList;
    private final FakeConfig fakeConfig;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private ForkJoinPool pool;

    public void getSiteList() {
        sitesList.getSites().forEach(site -> System.out.println(site.getUrl()));
    }

    @Async
    public void processSiteLinks() {
        initializeProcessing();

        pool.submit(() -> {
            sitesList.getSites().parallelStream().forEach(this::processSite);
        }).join();

        shutdownPool();
    }

    private void initializeProcessing() {
        stopRequested.set(false);

        if (paused.get()) {
            resumeProcessing();
        }

        if (pool == null || pool.isShutdown()) {
            pool = new ForkJoinPool();
        }
    }

    private void processSite(Site site) {
        if (stopRequested.get()) {
            System.out.println("Stopping site processing...");
            return;
        }

        String siteUrl = site.getUrl();
        System.out.println("Processing site: " + siteUrl);

        Set<String> links = LinkExtractor.getLinks(siteUrl, siteUrl, fakeConfig.getUserAgent(), fakeConfig.getReferrer());
        links.forEach(this::processLink);
    }

    private void processLink(String link) {
        if (stopRequested.get()) {
            System.out.println("Stopping link processing...");
            return;
        }

        handlePause();

        try {
            Thread.sleep(500); // Задержка 500 мс
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread interrupted while sleeping.");
        }

        System.out.println("Processing link: " + link);
    }

    private void handlePause() {
        while (paused.get()) {
            try {
                synchronized (paused) {
                    paused.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Thread interrupted while waiting.");
            }
        }
    }

    private void shutdownPool() {
        if (pool != null && !pool.isShutdown()) {
            pool.shutdown();
        }
    }

    public void stopProcessing() {
        if (pool != null && !pool.isShutdown()) {
            stopRequested.set(true);
            pool.shutdownNow();
            System.out.println("Processing stopped.");
        }
    }

    public void pauseProcessing() {
        paused.set(true);
        System.out.println("Processing paused.");
    }

    public void resumeProcessing() {
        synchronized (paused) {
            paused.set(false);
            paused.notifyAll();
        }
        System.out.println("Processing resumed.");
    }
}
