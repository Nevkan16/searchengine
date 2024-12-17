package searchengine.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.config.FakeConfig;
import searchengine.model.SiteEntity;
import searchengine.services.PageDataService;
import searchengine.utils.HtmlLoader;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
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
        } catch (Exception e) {
            log.error("Error processing links for: " + baseUrl, e);
        }

        return null;
    }

    private Set<LinkTask> processLinks(LinkProcessor linkProcessor) {
        if (stopProcessing.get()) return new HashSet<>();

        Set<LinkTask> subTasks = new HashSet<>();
        HtmlLoader htmlLoader = new HtmlLoader(fakeConfig);

        for (Element link : linkProcessor.extractLinks(doc)) {
            if (stopProcessing.get()) break;

            String linkHref = link.attr("abs:href");

            if (linkProcessor.shouldVisitLink(linkHref)) {
                linkProcessor.addVisitedLink(linkHref);
                log.info("Processing: {}", linkHref);

                try {
                    Document childDoc = htmlLoader.fetchHtmlDocument(linkHref); // Загружаем дочерний документ
                    savePageToDatabase(linkHref, childDoc); // Сохраняем в БД
                    subTasks.add(createSubTask(childDoc, linkHref));
                } catch (IOException e) {
                    log.error("Failed to load: " + linkHref, e);
                }
            }
        }

        return subTasks;
    }

    private void savePageToDatabase(String url, Document document) {
        try {
            String path = new URI(url).getPath(); // Извлекаем путь из URL
            int statusCode = 200;                 // HTTP-код ответа по умолчанию
            String content = document.html();     // HTML-содержимое страницы

            // Получаем SiteEntity из базы данных
            SiteEntity siteEntity = pageDataService.getSiteEntityByUrl(baseUrl);

            if (siteEntity != null) {
                // Сохраняем страницу в базе данных через сервис
                pageDataService.addPage(siteEntity, path, statusCode, content);
                log.info("Page saved to database: {}", path);
            } else {
                log.warn("SiteEntity not found for URL: {}", baseUrl);
            }
        } catch (Exception e) {
            log.error("Failed to save page to database: " + url, e);
        }
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
