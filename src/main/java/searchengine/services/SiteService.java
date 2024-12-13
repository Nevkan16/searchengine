package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.task.LinkExtractor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
            }
        });
    }

    public boolean isValidSite(String siteUrl) {
        try {
            // Создаем запрос
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(siteUrl))
                    .timeout(Duration.ofSeconds(5)) // Тайм-аут 5 секунд
                    .build();

            // Выполняем запрос и получаем ответ
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

//            if (response.statusCode() != 200) {
//                System.out.println("Ошибка ответа от сайта: " + siteUrl + " - HTTP " + response.statusCode());
//                return false; // Если статус не 200, сайт невалиден
//            }

            // Проверяем ссылки
            Set<String> links = LinkExtractor.getLinks(siteUrl, siteUrl, fakeConfig.getUserAgent(), fakeConfig.getReferrer());
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

        // Параллельная обработка валидных сайтов
        validSites.parallelStream().forEach(this::processSite);  // Используем только валидные сайты

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
