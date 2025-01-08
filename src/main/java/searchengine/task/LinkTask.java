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
import searchengine.services.SiteIndexingService;
import searchengine.utils.HtmlLoader;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
@Getter
public class LinkTask extends RecursiveTask<Void> {
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
                    siteCRUDService.updateSiteStatusToIndexed(getBaseUrl());
                    return true;
                }

                @Override
                public boolean isReleasable() {
                    return done;
                }
            });
        } catch (InterruptedException e) {
            log.error("Task for URL {} was interrupted {}", baseUrl, e.getMessage());
        }
        return null;
    }

    private void processTask() {
        if (SiteIndexingService.isStopProcessing()) {
            return;
        }
        LinkProcessor linkProcessor = new LinkProcessor(getBaseDomain());
        try {
            Set<LinkTask> subTasks = processLinks(linkProcessor);
            invokeAll(subTasks);
        } catch (Exception ignored) {
        }
    }

    private Set<LinkTask> processLinks(LinkProcessor linkProcessor) {
        AtomicBoolean stopFlag = SiteIndexingService.getStopFlagForSite(getBaseUrl());
        if (stopFlag == null || stopFlag.get()) return new HashSet<>();

        Set<LinkTask> subTasks = new HashSet<>();
        HtmlLoader htmlLoader = new HtmlLoader();
        SiteEntity siteEntity;

        try {
            siteEntity = siteCRUDService.getSiteByUrl(getBaseUrl());
        } catch (Exception e) {
            log.error("Failed to get SiteEntity for URL: {} {}", getBaseUrl(), e.getMessage());
            return subTasks;
        }

        for (Element link : linkProcessor.extractLinks(doc)) {
            if (stopFlag.get()) break;

            String linkHref = link.attr("abs:href");
            int currentDepth = calculateDepth(linkHref);

            if (currentDepth > maxDepth) {
                continue;
            }

            if (linkProcessor.shouldVisitLink(linkHref)) {
                log.info("Processing link at depth {}: {}", currentDepth, linkHref);

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
                return;
            }

            savePageToDatabase(linkHref, childDoc);
            subTasks.add(new LinkTask(childDoc, linkHref, depth, maxDepth, fakeConfig, siteCRUDService, pageProcessor));

        } catch (Exception e) {
            log.error("Unexpected error while processing URL: {} {}", linkHref, e.getMessage());
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

    private int calculateDepth(String url) {
        if (url == null || url.isEmpty()) {
            log.info("URL is null or empty: {}", url);
            return depth;
        }
        try {
            URI uri = new URI(url);
            URI baseUri = new URI(baseUrl);
            String uriPath = uri.getPath();
            String baseUriPath = baseUri.getPath();
            if (uriPath == null || baseUriPath == null) {
                return depth;
            }
            String relativePath = uriPath.replaceFirst(baseUriPath, "");
            if (relativePath.isEmpty()) {
                return 0;
            }
            return Math.max(0, relativePath.split("/").length - 1);
        } catch (URISyntaxException e) {
            log.info("Invalid URL syntax: {}", url);
        } catch (Exception e) {
            log.info("Unexpected error while calculating depth for URL: {}", url);
        }
        return depth;
    }

    private String getBaseDomain() {
        return HtmlLoader.getBaseUrl(baseUrl);
    }
}
