package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.task.LinkExtractor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

import static searchengine.task.LinkExtractor.getValidSites;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SitesList sitesList;
    private final FakeConfig fakeConfig;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ForkJoinPool pool;

    public Set<String> extractedLinks(String url) {
        return LinkExtractor.getLinks(url, url, fakeConfig.getUserAgent(), fakeConfig.getReferrer());
    }

    @Async
    public void processSiteLinks() {
        if (isRunning.get()) {
            System.out.println("Processing is already running. Returning...");
            return; // Если процесс уже выполняется, выходим
        }

        if (stopRequested.get()) {
            System.out.println("Processing was stopped. Restarting...");
            stopRequested.set(false); // Сброс флага остановки, чтобы процесс можно было продолжить
        }

        initializeProcessing();

        isRunning.set(true); // Устанавливаем флаг, что процесс запущен

        // Используем новый метод для получения валидных сайтов
        List<Site> validSitesList = getValidSites(sitesList);

        // Параллельная обработка валидных сайтов
        validSitesList.parallelStream().forEach(this::processSite);  // Используем только валидные сайты

        shutdownPool();
        isRunning.set(false); // После завершения процесса сбрасываем флаг
    }

    private void initializeProcessing() {
        stopRequested.set(false);

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

        Set<String> links = extractedLinks(siteUrl);
        links.forEach(this::processLink);
    }

    private void processLink(String link) {
        if (stopRequested.get()) {
            return;
        }

        try {
            Thread.sleep(500); // Задержка 500 мс
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread interrupted while sleeping.");
        }

        System.out.println("Processing link: " + link);
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

    public boolean isRunning() {
        return isRunning.get();
    }
}
