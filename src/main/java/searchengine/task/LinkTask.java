package searchengine.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.transaction.UnexpectedRollbackException;
import searchengine.config.FakeConfig;
import searchengine.model.SiteEntity;
import searchengine.services.PageDataService;
import searchengine.utils.HtmlLoader;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class LinkTask extends RecursiveTask<Void> {
    private static final AtomicBoolean stopProcessing = new AtomicBoolean(false);

    private final Document doc;      // HTML-документ текущей страницы
    private final String baseUrl;    // Полный URL текущей страницы
    private final int depth;         // Текущая глубина
    private final int maxDepth;      // Максимальная глубина
    private final FakeConfig fakeConfig;
    private final PageDataService pageDataService; // Сервис для сохранения страниц

    @Override
    protected Void compute() {
        // Проверяем условие для остановки задачи
        if (depth > maxDepth || stopProcessing.get()) {
            return null;
        }

        try {
            LinkProcessor linkProcessor = new LinkProcessor(getBaseDomain());
            savePageToDatabase(baseUrl, doc); // Сохраняем текущую страницу в БД
            Set<LinkTask> subTasks = processLinks(linkProcessor);
            if (!stopProcessing.get()) {
                invokeAll(subTasks);
            }
        } catch (CancellationException e) {
            log.warn("Task for URL {} was cancelled", baseUrl);
        } catch (Exception e) {
            log.error("Error processing links for: {}", baseUrl, e);
        }
        return null;
    }


    private Set<LinkTask> processLinks(LinkProcessor linkProcessor) {
        if (stopProcessing.get()) return new HashSet<>();

        Set<LinkTask> subTasks = new HashSet<>();
        HtmlLoader htmlLoader = new HtmlLoader();

        for (Element link : linkProcessor.extractLinks(doc)) {
            if (stopProcessing.get()) break;

            String linkHref = link.attr("abs:href");

            if (linkProcessor.shouldVisitLink(linkHref)) {
                linkProcessor.addVisitedLink(linkHref);
                log.info("Processing: {}", linkHref);

                try {
                    // Загружаем дочерний документ
                    Document childDoc = htmlLoader.fetchHtmlDocument(linkHref, fakeConfig);

                    if (childDoc == null) {
                        log.error("Failed to load child document for URL: {}", linkHref);
                        continue; // Пропускаем текущую ссылку
                    }

                    savePageToDatabase(linkHref, childDoc);

                    subTasks.add(createSubTask(childDoc, linkHref));

                } catch (Exception e) {
                    log.error("Unexpected error while processing URL: {}", linkHref, e);
                }
            }
        }

        return subTasks;
    }



    private void savePageToDatabase(String url, Document document) {
        SiteEntity siteEntity = null;
        try {
            HtmlLoader htmlLoader = new HtmlLoader();
            String path = new URI(url).getPath();
            int statusCode = htmlLoader.getHttpStatusCode(url);
            String content = document.html();

            // Проверяем, пустая ли страница (например, если нет видимого контента)
            if (isEmptyPage(content)) {
                log.info("Skipping empty page: {}", url);
                return; // Пропускаем сохранение пустой страницы
            }

            // Получаем SiteEntity из базы данных
            siteEntity = pageDataService.getSiteEntityByUrl(baseUrl);

            // Сохраняем страницу в базе данных через сервис
            pageDataService.addPage(siteEntity, path, statusCode, content);
            log.info("Page saved to database: {}", path);
        } catch (UnexpectedRollbackException e) {
            log.error("Transaction rollback occurred for page: {}", url);
            if (siteEntity != null) {
                pageDataService.updateSiteError(siteEntity, "Error load child page:");
            }
        } catch (Exception e) {
            log.error("Failed to save page to database: " + url, e);
            if (siteEntity != null) {
                pageDataService.updateSiteError(siteEntity, "Error saving page: " + e.getMessage());
            }
        }
    }

    private boolean isEmptyPage(String content) {
        // Простой способ проверки на пустую страницу: если нет содержимого внутри <body>
        return content == null || content.trim().isEmpty() || content.equals("<html><head></head><body></body></html>");
    }


    private String getBaseDomain() {
        try {
            return new URI(baseUrl).getHost();
        } catch (Exception e) {
            log.error("Invalid base URL: {}", baseUrl, e);
            return "";
        }
    }

    private LinkTask createSubTask(Document childDoc, String linkHref) {
        return new LinkTask(childDoc, linkHref, depth + 1, maxDepth, fakeConfig, pageDataService);
    }

    public static void resetStopFlag() {
        stopProcessing.set(false);
    }

    public static void stopProcessing() {
        stopProcessing.set(true);
    }
}
