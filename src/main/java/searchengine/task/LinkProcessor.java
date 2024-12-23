package searchengine.task;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class LinkProcessor {
    private static final Set<String> visitedLinks = Collections.synchronizedSet(ConcurrentHashMap.newKeySet());
    private final String baseDomain;

    public Elements extractLinks(Document doc) {
        return doc.select("a[href]");
    }

    public boolean shouldVisitLink(String linkHref) {
        return isValidLink(linkHref) && !visitedLinks.contains(linkHref) && !isMainPage(linkHref);
    }

    public void addVisitedLink(String linkHref) {
        visitedLinks.add(linkHref);
    }

    private boolean isMainPage(String linkHref) {
        try {
            URI uri = new URI(linkHref);
            URI baseUri = new URI(baseDomain);
            return normalizeDomain(uri.getHost()).equals(normalizeDomain(baseUri.getHost())) &&
                    (uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static void clearVisitedLinks() {
        visitedLinks.clear();
    }

    public boolean isValidLink(String linkHref) {
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

    private String normalizeDomain(String domain) {
        if (domain == null) return null;
        String[] parts = domain.split("\\.");
        if (parts.length > 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return domain;
    }
}

