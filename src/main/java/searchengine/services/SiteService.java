package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.entity.SiteEntity;
import searchengine.repository.SiteRepository;
import searchengine.task.LinkTask;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private final SiteRepository siteRepository;
    private final PageService pageService;
    private final SitesList sitesList;
    private ForkJoinPool forkJoinPool;

    public SiteEntity createOrUpdateSite(String url, String name) {
        SiteEntity site = siteRepository.findByUrl(url).orElseGet(() -> {
            SiteEntity newSite = new SiteEntity();
            newSite.setUrl(url);
            newSite.setName(resolveName(name, url));
            return newSite;
        });

        site.setStatus(SiteEntity.Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(null);
        return siteRepository.save(site);
    }

    public void indexAllSites() {
        LinkTask.resetStopFlag();

        // Перезапуск ForkJoinPool, если он был завершен
        initializeForkJoinPool();

        if (LinkTask.getStopRequest()) {
            logIndexStopped();
            return;
        }

        try {
            // Обрабатываем все сайты для индексации
            processSites();
        } finally {
            forkJoinPool.shutdown();
            logIndexStopped();
        }
    }

    private void initializeForkJoinPool() {
        if (forkJoinPool == null || forkJoinPool.isShutdown()) {
            forkJoinPool = new ForkJoinPool();
        }
    }

    private void processSites() {
        // Индексация сайтов из настроек
        for (Site site : sitesList.getSites()) {
            if (LinkTask.getStopRequest()) {
                logIndexStopped();
                return;
            }

            createOrUpdateSite(site.getUrl(), site.getName());

            // Запуск индексации сайта в отдельном потоке
            indexSiteInNewThread(site.getUrl());
        }

        // Индексация всех сайтов из репозитория
        Iterable<SiteEntity> sites = siteRepository.findAll();
        for (SiteEntity site : sites) {
            if (LinkTask.getStopRequest()) {
                logIndexStopped();
                return;
            }

            // Запуск индексации сайта в отдельном потоке
            indexSiteInNewThread(site.getUrl());
        }
    }

    private void indexSiteInNewThread(String url) {
        forkJoinPool.submit(() -> {
            try {
                Document rootDoc = Jsoup.connect(url).get();
                SiteEntity site = siteRepository.findByUrl(url).orElseThrow(() -> new IOException("Site not found"));

                LinkTask linkTask = new LinkTask(rootDoc, site.getUrl(), 1, 3, pageService, site);

                // Выполняем задачу и ожидаем её завершения
                forkJoinPool.submit(linkTask).join();

                site.setStatus(SiteEntity.Status.INDEXED);
                siteRepository.save(site);
                if (LinkTask.getStopRequest()) {
                    logIndexStopped();
                }
            } catch (IOException e) {
                // В случае ошибки меняем статус сайта на FAILED
                SiteEntity site = siteRepository.findByUrl(url).orElseThrow(() -> new RuntimeException("Site not found"));
                site.setStatus(SiteEntity.Status.FAILED);
                site.setLastError(e.getMessage());
                siteRepository.save(site);
                if (LinkTask.getStopRequest()) {
                    logIndexStopped();
                }
            }
        });
    }

    private String resolveName(String name, String url) {
        return (name == null || name.isEmpty()) ? url : name;
    }

    public void stopIndexing() {
        LinkTask.requestStop();
        logIndexStopped();
    }

    private void logIndexStopped() {
        log.info("Indexing has been stopped");
    }
}
