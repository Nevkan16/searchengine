package searchengine.task;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LinkExtractor {

    private static final Set<String> visitedLinks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static Set<String> getLinks(String url, String baseUrl) {
        Set<String> validLinks = new HashSet<>();

        try {
            Document doc = Jsoup.connect(url).get();
            Elements links = doc.select("a[href]");

            for (Element link : links) {
                String linkHref = link.attr("abs:href");

                if (LinkValidator.isValid(linkHref, baseUrl) && visitedLinks.add(linkHref)) {
                    validLinks.add(linkHref);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading page: " + url);
        }

        return validLinks;
    }

    private static class LinkValidator {

        public static boolean isValid(String linkHref, String baseDomain) {
            try {
                URI uri = new URI(linkHref);
                String linkDomain = uri.getHost();

                String normalizedBaseDomain = normalizeDomain(baseDomain);
                String normalizedLinkDomain = normalizeDomain(linkDomain);

                String scheme = uri.getScheme();
                if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                    return false;
                }

                if (linkDomain == null) return true;

                if (!normalizedBaseDomain.equals(normalizedLinkDomain)) {
                    return false;
                }

                if (uri.getFragment() != null || linkHref.isEmpty()) {
                    return false;
                }

                String path = uri.getPath();
                return path == null || (!path.endsWith(".pdf") && !path.endsWith(".jpg") &&
                        !path.endsWith(".png") && !path.endsWith(".zip"));

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
}

