package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.task.LinkExtractor;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SitesList sitesList;
    private final FakeConfig fakeConfig;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ForkJoinPool pool;

    // Новый список валидных сайтов
    private final Set<Site> validSites = new HashSet<>();

    // Метод для проверки валидности сайтов
    private void validateSites() {
        validSites.clear();  // Очищаем список перед проверкой

        sitesList.getSites().forEach(site -> {
            String siteUrl = site.getUrl();
            if (isValidSite(siteUrl)) {
                validSites.add(site);
            }
        });
    }

    public Set<String> extractedLinks(String url) {
        return LinkExtractor.getLinks(url, url, fakeConfig.getUserAgent(), fakeConfig.getReferrer());
    }

    public boolean isValidSite(String siteUrl) {
        try {
            // Проверяем ссылки
            Set<String> links = extractedLinks(siteUrl);
            if (links.isEmpty()) {
                System.out.println("Ссылки не найдены на сайте: " + siteUrl);
                return false; // Если ссылок нет, сайт невалиден
            }

            System.out.println("Сайт валиден: " + siteUrl);
            return true; // Ссылки найдены, сайт валиден

        } catch (Exception e) {
            System.out.println("Ошибка при проверке сайта: " + siteUrl + " - " + e.getMessage());
            return false; // Если произошла ошибка, сайт невалиден
        }
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

        // Проверка валидности сайтов перед обработкой ссылок
        validateSites();

        // Параллельная обработка валидных сайтов
        validSites.parallelStream().forEach(this::processSite);  // Используем только валидные сайты

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
