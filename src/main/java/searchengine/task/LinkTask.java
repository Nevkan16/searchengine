package searchengine.task;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.transaction.UnexpectedRollbackException;
import searchengine.config.FakeConfig;
import searchengine.constants.ErrorMessages;
import searchengine.model.SiteEntity;
import searchengine.services.PageProcessor;
import searchengine.services.SiteCRUDService;
import searchengine.utils.HtmlLoader;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
@Getter
public class LinkTask extends RecursiveTask<Void> {
    private static final AtomicBoolean stopProcessing = new AtomicBoolean(false);

    private final Document doc;
    private final String baseUrl;
    private final int depth;
    private final int maxDepth;
    private final FakeConfig fakeConfig;
    private final SiteCRUDService siteCRUDService;
    private final PageProcessor pageProcessor;

    @Override
    protected Void compute() {
        try {
            ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                private boolean done = false;

                @Override
                public boolean block() {
                    processTask();
                    done = true;
                    return true;
                }

                @Override
                public boolean isReleasable() {
                    return done;
                }
            });
        } catch (InterruptedException e) {
            log.error("Task for URL {} was interrupted", baseUrl, e);
        }
        return null;
    }

    private void processTask() {
        if (depth > maxDepth || stopProcessing.get()) {
            return;
        }

        LinkProcessor linkProcessor = new LinkProcessor(getBaseDomain());
        linkProcessor.addVisitedLink(baseUrl);

        try {
            Set<LinkTask> subTasks = processLinks(linkProcessor);
            if (!stopProcessing.get()) {
                invokeAll(subTasks);
            }
        } catch (Exception e) {
            log.error("Error processing links for: {}", baseUrl, e);
        }
    }



    private Set<LinkTask> processLinks(LinkProcessor linkProcessor) {
        if (stopProcessing.get()) return new HashSet<>();

        Set<LinkTask> subTasks = new HashSet<>();
        HtmlLoader htmlLoader = new HtmlLoader();
        SiteEntity siteEntity;

        try {
            siteEntity = siteCRUDService.getSiteByUrl(getBaseUrl());
        } catch (Exception e) {
            log.error("Failed to retrieve SiteEntity for URL: {}", getBaseUrl(), e);
            return subTasks;
        }

        for (Element link : linkProcessor.extractLinks(doc)) {
            if (stopProcessing.get()) break;

            String linkHref = link.attr("abs:href");

            if (linkProcessor.shouldVisitLink(linkHref)) {
                linkProcessor.addVisitedLink(linkHref);
                log.info("Processing: {}", linkHref);

                processLink(linkHref, htmlLoader, siteEntity, subTasks);
            }
        }

        return subTasks;
    }

    private void processLink(String linkHref, HtmlLoader htmlLoader, SiteEntity siteEntity, Set<LinkTask> subTasks) {
        try {
            Document childDoc = htmlLoader.fetchHtmlDocument(linkHref, fakeConfig);

            if (childDoc == null) {
                log.error("Failed to load child document for URL: {}", linkHref);
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.ERROR_LOAD_CHILD_PAGE);
                return; // Пропускаем текущую ссылку
            }

            savePageToDatabase(linkHref, childDoc);
            subTasks.add(createSubTask(childDoc, linkHref));

        } catch (Exception e) {
            log.error("Unexpected error while processing URL: {}", linkHref, e);
            siteCRUDService.updateSiteError(siteEntity, ErrorMessages.UNKNOWN_ERROR);
        }
    }


    private void savePageToDatabase(String url, Document document) {
        SiteEntity siteEntity = null;
        try {
            siteEntity = siteCRUDService.getSiteByUrl(getBaseUrl());
            pageProcessor.saveAndProcessPage(url, document, siteEntity);
        } catch (UnexpectedRollbackException e) {
            log.error("Transaction rollback occurred for page: {}", url);
            if (siteEntity != null) {
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.SITE_UNAVAILABLE);
            }
        } catch (Exception e) {
            log.error("Failed to save page to database: " + url, e);
            if (siteEntity != null) {
                siteCRUDService.updateSiteError(siteEntity, ErrorMessages.ERROR_SAVE_PAGE_TO_DATABASE);
            }
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
        return new LinkTask(childDoc, linkHref, depth + 1, maxDepth, fakeConfig, siteCRUDService, pageProcessor);
    }

    public static void resetStopFlag() {
        stopProcessing.set(false);
    }

    public static void stopProcessing() {
        stopProcessing.set(true);
    }
}
