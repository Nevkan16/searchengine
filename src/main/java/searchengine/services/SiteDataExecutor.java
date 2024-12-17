package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
@Service
@RequiredArgsConstructor
public class SiteDataExecutor {

    private final DataService dataService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public void refreshAllSitesData() {
        System.out.println("Обновление данных для всех сайтов...");

        List<Site> allSites = dataService.getAllSites();
        if (allSites.isEmpty()) {
            System.out.println("Список сайтов пуст. Обновление завершено.");
            return;
        }

        try {
            allSites.forEach(site -> executorService.submit(() -> processSite(site)));
        } finally {
            shutdownExecutor();
        }

        System.out.println("Обновление данных завершено.");
    }

    private void processSite(Site site) {
        String siteUrl = site.getUrl();
        System.out.println("Обработка сайта: " + siteUrl);

        dataService.deleteSiteByUrl(siteUrl);
        dataService.createSiteRecord(site);
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
}
