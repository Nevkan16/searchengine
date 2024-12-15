package searchengine.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.FakeConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
public class LinkTask extends RecursiveTask<Void> {
    private static final Set<String> visitedLinks = Collections.synchronizedSet(new HashSet<>());
    private static final AtomicBoolean stopProcessing = new AtomicBoolean(false);
    private final Document doc;
    private final String baseUrl;
    private final int depth;
    private final int maxDepth;
    private final FakeConfig fakeConfig; // Добавлен FakeConfig

    @Override
    protected Void compute() {
        if (depth > maxDepth || stopProcessing.get()) {
            return null;
        }

        try {
            Set<LinkTask> subTasks = processLinks();
            if (!stopProcessing.get()) {
                invokeAll(subTasks);
            }
        } catch (URISyntaxException e) {
            log.error("Invalid URI: " + baseUrl, e);
        }

        return null;
    }

    private Set<LinkTask> processLinks() throws URISyntaxException {
        if (stopProcessing.get()) return Collections.emptySet();

        URI uri = new URI(baseUrl);
        String domain = uri.getHost();

        Elements links = extractLinks();
        Set<LinkTask> subTasks = new HashSet<>();

        for (Element link : links) {
            if (stopProcessing.get()) break;

            String linkHref = link.attr("abs:href");

            if (shouldVisitLink(linkHref, domain)) {
                addVisitedLink(linkHref);
                logLinkProcessing(linkHref);

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

    public static void resetStopFlag() {
        stopProcessing.set(false);
    }

    public static void stopProcessing() {
        stopProcessing.set(true);
    }

    private Elements extractLinks() {
        return doc.select("a[href]");
    }

    private boolean shouldVisitLink(String linkHref, String domain) {
        return isValidLink(linkHref, domain) && !visitedLinks.contains(linkHref);
    }

    private void addVisitedLink(String linkHref) {
        synchronized (visitedLinks) {
            visitedLinks.add(linkHref);
        }
    }

    private void logLinkProcessing(String linkHref) {
        System.out.println("Processing: " + linkHref);
    }

    private Document loadDocument(String linkHref) throws IOException {
        try {
            // Задержка перед запросом
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        return Jsoup.connect(linkHref)
                .userAgent(fakeConfig.getUserAgent()) // Использование user-agent из FakeConfig
                .referrer(fakeConfig.getReferrer())   // Использование referrer из FakeConfig
                .get();
    }


    private LinkTask createSubTask(Document childDoc, String linkHref) {
        return new LinkTask(childDoc, linkHref, depth + 1, maxDepth, fakeConfig);
    }

    public static boolean isValidLink(String linkHref, String baseDomain) {
        Set<String> INVALID_EXTENSIONS = Set.of(
                ".pdf", ".jpg", ".png", ".zip", ".docx", ".xlsx", ".gif", ".mp4", ".mp3", ".php", ".jpeg"
        );
        try {
            URI uri = new URI(linkHref);
            String linkDomain = uri.getHost();

            String normalizedBaseDomain = normalizeDomain(baseDomain);
            String normalizedLinkDomain = normalizeDomain(linkDomain);

            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                return false;
            }

            if (linkDomain == null) {
                return true;
            }

            if (!normalizedBaseDomain.equals(normalizedLinkDomain)) {
                return false;
            }

            if (uri.getFragment() != null || linkHref.isEmpty()) {
                return false;
            }

            String path = uri.getPath();
            if (path != null) {
                String lowerCasePath = path.toLowerCase(Locale.ROOT);
                for (String ext : INVALID_EXTENSIONS) {
                    if (lowerCasePath.endsWith(ext)) {
                        return false;
                    }
                }
            }
            return true;

        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String normalizeDomain(String domain) {
        if (domain == null) return null;
        String[] parts = domain.split("\\.");
        if (parts.length > 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return domain;
    }
}