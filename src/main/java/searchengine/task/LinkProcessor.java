package searchengine.task;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class LinkProcessor {
    private static final Set<String> visitedLinks = Collections.synchronizedSet(ConcurrentHashMap.newKeySet());
    private static final Set<String> INVALID_EXTENSIONS = Set.of(
            ".pdf", ".jpg", ".png", ".zip", ".docx", ".xlsx", ".gif", ".mp4", ".mp3", ".php", ".jpeg"
    );
    private final String baseDomain;

    public Elements extractLinks(Document doc) {
        return doc.select("a[href]");
    }

    public boolean shouldVisitLink(String linkHref) {
        String normalizedLink = normalizeUrl(linkHref);
        return isValidLink(normalizedLink) && visitedLinks.add(normalizedLink) && !isMainPage(normalizedLink);
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(url).normalize();
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost();
            String path = (uri.getPath() == null || uri.getPath().isEmpty()) ? "/" : uri.getPath();
            return new URI(scheme, null, host, uri.getPort(), path, null, null).toString();
        } catch (URISyntaxException e) {
            return ""; // Возвращаем пустую строку для некорректных URL
        }
    }

    private boolean isMainPage(String url) {
        try {
            URI uri = new URI(url);
            URI baseUri = new URI(baseDomain);
            return normalizeDomain(uri.getHost()).equals(normalizeDomain(baseUri.getHost())) &&
                    "/".equals(uri.getPath());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static boolean hasHtmlDoctype(String content) {
        if (content == null) return false;
        String htmlDoctypePattern = "<!doctype\\s+html.*?>";
        return Pattern.compile(htmlDoctypePattern, Pattern.CASE_INSENSITIVE).matcher(content).find();
    }

    public static boolean isEmptyPage(String content) {
        return content == null || content.trim().isEmpty() ||
                content.equals("<html><head></head><body></body></html>") ||
                !hasHtmlDoctype(content);
    }

    public static void clearVisitedLinks() {
        visitedLinks.clear();
    }

    public boolean isValidLink(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();

            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https")) || host == null) {
                return false;
            }

            if (!normalizeDomain(baseDomain).equals(normalizeDomain(host))) {
                return false;
            }

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
        if (domain == null) return "";
        String[] parts = domain.split("\\.");
        return parts.length > 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : domain;
    }
}
