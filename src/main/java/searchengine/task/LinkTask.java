package searchengine.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import searchengine.config.FakeConfig;

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
    private final Document doc;
    private final String baseUrl;
    private final int depth;
    private final int maxDepth;
    private final FakeConfig fakeConfig;

    @Override
    protected Void compute() {
        if (depth > maxDepth || stopProcessing.get()) {
            return null;
        }

        try {
            LinkProcessor linkProcessor = new LinkProcessor(getBaseDomain());
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
        for (Element link : linkProcessor.extractLinks(doc)) {
            if (stopProcessing.get()) break;

            String linkHref = link.attr("abs:href");

            if (linkProcessor.shouldVisitLink(linkHref)) {
                linkProcessor.addVisitedLink(linkHref);
                log.info("Processing: {}", linkHref);

                try {
                    Document childDoc = loadDocument(linkHref);
                    LinkTask subTask = createSubTask(childDoc, linkHref);
                    subTasks.add(subTask);
                } catch (IOException e) {
                    log.error("Failed to load: " + linkHref, e);
                }
            }
        }

        return subTasks;
    }

    private String getBaseDomain() {
        try {
            return new URI(baseUrl).getHost();
        } catch (Exception e) {
            log.error("Invalid base URL: {}", baseUrl, e);
            return "";
        }
    }

    private Document loadDocument(String linkHref) throws IOException {
        try {
            Thread.sleep(1000); // Delay before request
        } catch (InterruptedException ignored) {
        }
        return Jsoup.connect(linkHref)
                .userAgent(fakeConfig.getUserAgent())
                .referrer(fakeConfig.getReferrer())
                .get();
    }

    private LinkTask createSubTask(Document childDoc, String linkHref) {
        return new LinkTask(childDoc, linkHref, depth + 1, maxDepth, fakeConfig);
    }

    public static void resetStopFlag() {
        stopProcessing.set(false);
    }

    public static void stopProcessing() {
        stopProcessing.set(true);
    }
}
