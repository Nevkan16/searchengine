package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
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
    private final AtomicBoolean paused = new AtomicBoolean(false);  // Флаг приостановки
    private ForkJoinPool pool;

    public void getSiteList() {
        for (Site list : sitesList.getSites()) {
            System.out.println(list.getUrl());
        }
    }

    @Async  // Сделаем процесс обработки асинхронным
    public void processSiteLinks() {
        stopRequested.set(false);

        // Если процесс на паузе, снимаем паузу и продолжаем
        if (paused.get()) {
            resumeProcessing();  // Вызываем resumeProcessing, чтобы продолжить выполнение
        }

        // Пересоздаем пул, если он был закрыт
        if (pool == null || pool.isShutdown()) {
            pool = new ForkJoinPool();
        }

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

                    // Проверка флага на паузу
                    while (paused.get()) {
                        try {
                            synchronized (paused) {
                                paused.wait();  // Приостановка потока
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.out.println("Thread interrupted while waiting.");
                        }
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

    // Метод для приостановки работы
    public void pauseProcessing() {
        paused.set(true);  // Устанавливаем флаг паузы
        System.out.println("Processing paused.");
    }

    // Метод для продолжения работы
    public void resumeProcessing() {
        synchronized (paused) {
            paused.set(false);  // Снимаем флаг паузы
            paused.notifyAll();  // Разблокируем потоки
        }
        System.out.println("Processing resumed.");
    }
}
