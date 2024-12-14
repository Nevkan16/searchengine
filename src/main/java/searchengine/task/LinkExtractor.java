package searchengine.task;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.Site;
import searchengine.config.SitesList;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class LinkExtractor {

    public static Set<String> getLinks(String baseUrl, String url, String userAgent, String referrer) {
        Set<String> validLinks = new HashSet<>();
        Set<String> visitedLinks = new HashSet<>();

        Document doc = connectToUrl(url, userAgent, referrer);
        if (doc != null) {
            for (Element link : extractLinks(doc)) {
                String linkHref = link.attr("abs:href");

                if (isValidLink(linkHref, baseUrl) && visitedLinks.add(linkHref)) {
                    validLinks.add(linkHref);
                }
            }
        }

        return validLinks;
    }

    public static List<Site> getValidSites(SitesList sitesList) {
        List<Site> validSites = new ArrayList<>();

        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            System.out.println("Checking site: " + siteUrl);

            Document doc = connectToUrl(siteUrl, null, null);
            if (doc != null && !extractLinks(doc).isEmpty()) {
                validSites.add(site);
                System.out.println("Site is valid: " + siteUrl);
            } else {
                System.out.println("Site is invalid: " + siteUrl + " (No links found)");
            }
        }

        System.out.println("Total valid sites: " + validSites.size());
        return validSites;
    }

    // Метод для проверки валидности ссылки
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

    // Метод для нормализации домена
    private static String normalizeDomain(String domain) {
        if (domain == null) return null;
        String[] parts = domain.split("\\.");
        if (parts.length > 2) {
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        return domain;
    }

    // Метод для подключения через Jsoup
    private static Document connectToUrl(String url, String userAgent, String referrer) {
        try {
            if (userAgent != null && referrer != null) {
                return Jsoup.connect(url)
                        .userAgent(userAgent)
                        .referrer(referrer)
                        .get();
            } else {
                return Jsoup.connect(url).get();
            }
        } catch (IOException e) {
            System.err.println("Error connecting to URL: " + url + " (" + e.getMessage() + ")");
            return null;
        }
    }

    // Метод для извлечения ссылок
    private static Elements extractLinks(Document doc) {
        return doc.select("a[href]");
    }
}
