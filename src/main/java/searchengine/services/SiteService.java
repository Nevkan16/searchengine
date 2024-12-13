package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.task.LinkExtractor;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
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
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ForkJoinPool pool;

    // Новый список валидных сайтов
    private final Set<Site> validSites = new HashSet<>();

    public void getSiteList() {
        sitesList.getSites().forEach(site -> System.out.println(site.getUrl()));
    }

    // Метод для проверки валидности сайтов
    private void validateSites() {
        validSites.clear();  // Очищаем список перед проверкой

        sitesList.getSites().forEach(site -> {
            String siteUrl = site.getUrl();
            if (isValidSite(siteUrl)) {
                validSites.add(site);
                System.out.println("Valid site: " + siteUrl);
            } else {
                System.out.println("Invalid site: " + siteUrl);
            }
        });
    }

    // Проверка сайта на валидность
    private boolean isValidSite(String siteUrl) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(siteUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // Тайм-аут 5 секунд
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Сайт доступен, теперь проверяем ссылки
                Set<String> links = LinkExtractor.getLinks(siteUrl, siteUrl, fakeConfig.getUserAgent(), fakeConfig.getReferrer());
                return !links.isEmpty();  // Если хотя бы одна ссылка есть, сайт валиден
            }
        } catch (IOException e) {
            System.out.println("Error checking site: " + siteUrl + " - " + e.getMessage());
        }
        return false;  // Если ошибка или нет ссылок, сайт невалиден
    }

    @Async
    public void processSiteLinks() {
        if (isRunning.get()) {
            if (paused.get()) {
                System.out.println("Processing is paused. Resuming...");
                resumeProcessing(); // Если процесс на паузе, возобновляем его
            } else {
                System.out.println("Processing is already running. Returning...");
                return; // Если процесс уже выполняется, выходим
            }
        }

        if (stopRequested.get()) {
            System.out.println("Processing was stopped. Restarting...");
            stopRequested.set(false); // Сброс флага остановки, чтобы процесс можно было продолжить
        }

        initializeProcessing();

        isRunning.set(true); // Устанавливаем флаг, что процесс запущен

        // Проверка валидности сайтов перед обработкой ссылок
        validateSites();

        pool.submit(() -> {
            validSites.parallelStream().forEach(this::processSite);  // Используем только валидные сайты
        }).join();

        shutdownPool();
        isRunning.set(false); // После завершения процесса сбрасываем флаг
    }

    public void stopPool() {
        pool.shutdownNow();
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
