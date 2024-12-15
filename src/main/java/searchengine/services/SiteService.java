package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.task.LinkTask;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class SiteService {

    private final SitesList sitesList;
    private ForkJoinPool forkJoinPool = new ForkJoinPool(); // Создаём пул потоков
    private final AtomicBoolean isProcessing = new AtomicBoolean(false); // Состояние обработки
    private final FakeConfig fakeConfig;

    public void processSites() {
        if (isProcessing.get()) {
            System.out.println("Processing is already running!");
            return; // Если процесс уже запущен, выходим
        }

        isProcessing.set(true); // Устанавливаем состояние "индексация запущена"
        LinkTask.stopProcessing(); // Убедимся, что старые задачи остановлены
        LinkTask.resetStopFlag();  // Сбрасываем флаг остановки для новых задач

        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdown(); // Останавливаем старый пул потоков, если он не завершён
        }
        forkJoinPool = new ForkJoinPool(); // Создаём новый пул потоков

        forkJoinPool.execute(() -> {
            System.out.println("Indexing started...");

            try {
                List<Site> sites = getValidSites();
                List<LinkTask> tasks = new ArrayList<>();

                for (Site site : sites) {
                    String siteUrl = site.getUrl();
                    try {
                        Document doc = Jsoup.connect(siteUrl).get();
                        LinkTask linkTask = new LinkTask(doc, siteUrl, 0, 2, fakeConfig);
                        tasks.add(linkTask);
                        forkJoinPool.execute(linkTask);
                    } catch (IOException e) {
                        System.out.println("Error processing site: " + siteUrl);
                    }
                }

                for (LinkTask task : tasks) {
                    task.join();
                }

                System.out.println("Indexing completed.");
            } catch (Exception e) {
                System.out.println("Error during indexing: " + e.getMessage());
            } finally {
                forkJoinPool.shutdown();
                isProcessing.set(false); // Индексация завершена
            }
        });
    }

    public synchronized void stopProcessing() {
        if (!isProcessing.get()) {
            System.out.println("Processing is not running!");
            return;
        }

        System.out.println("Stopping processing...");
        LinkTask.stopProcessing();

        if (forkJoinPool != null && !forkJoinPool.isShutdown()) {
            forkJoinPool.shutdownNow(); // Останавливаем пул потоков
        }

        isProcessing.set(false); // Устанавливаем состояние "индексация остановлена"
    }


    public List<Site> getValidSites() {
        List<Site> validSites = new ArrayList<>();

        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            System.out.println("Checking site: " + siteUrl);

            try {
                Document doc = Jsoup.connect(siteUrl).get();
                Elements links = doc.select("a[href]");

                if (!links.isEmpty()) {
                    validSites.add(site);
                    System.out.println("Site is valid: " + siteUrl);
                } else {
                    System.out.println("Site is invalid: " + siteUrl + " (No links found)");
                }
            } catch (IOException e) {
                System.out.println("Error processing site: " + siteUrl + " (" + e.getMessage() + ")");
            }
        }

        System.out.println("Total valid sites: " + validSites.size());
        return validSites;
    }
}
