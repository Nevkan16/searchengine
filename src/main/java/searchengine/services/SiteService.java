package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private ForkJoinPool pool;

    public void getSiteList() {
        for (Site list : sitesList.getSites()) {
            System.out.println(list.getUrl());
        }
    }

    public void processSiteLinks() {
        stopRequested.set(false);
        pool = new ForkJoinPool();

        // Обработка каждого сайта в отдельном потоке
        pool.submit(() -> {
            sitesList.getSites().parallelStream().forEach(site -> {
                if (stopRequested.get()) {
                    System.out.println("Stopping site processing...");
                    return;
                }

                String siteUrl = site.getUrl();
                System.out.println("Processing site: " + siteUrl);

                Set<String> links = LinkExtractor.getLinks(siteUrl, siteUrl);
                links.forEach(link -> {
                    if (stopRequested.get()) {
                        System.out.println("Stopping link processing...");
                        return;
                    }

                    // Задержка перед открытием новой ссылки
                    try {
                        Thread.sleep(500); // Задержка 500 мс (0.5 сек.)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.out.println("Thread interrupted while sleeping.");
                    }

                    System.out.println("Processing link: " + link);
                });
            });
        }).join();

        pool.shutdown();
    }

    public void stopProcessing() {
        if (pool != null && !pool.isShutdown()) {
            stopRequested.set(true);
            pool.shutdownNow();
            System.out.println("Processing stopped.");
        }
    }

}
