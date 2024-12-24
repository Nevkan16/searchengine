package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigUtil {

    // Регулярное выражение для проверки общих URL
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?(www\\.)?[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$");

    // Регулярное выражение для проверки localhost
    private static final Pattern LOCALHOST_PATTERN = Pattern.compile(
            "^http://localhost(:\\d+)?(/.*)?$");

    private final SitesList sitesList;

    // Порт из конфигурации
    @Value("${server.port}")
    private int serverPort;

    public String getSiteNameFromConfig(String url) {
        List<Site> availableSites = getAvailableSites();

        if (availableSites.isEmpty()) {
            log.info("Список сайтов пуст.");
            return null;
        }

        return getSiteName(url, availableSites);
    }

    List<Site> getAvailableSites() {
        if (sitesList == null || sitesList.getSites().isEmpty()) {
            return Collections.emptyList();
        }

        List<Site> sites = sitesList.getSites();
        Set<String> seenUrls = new HashSet<>();
        boolean hasDuplicates = false;

        for (Site site : sites) {
            if (!seenUrls.add(site.getUrl())) {
                hasDuplicates = true;
            }
        }

        if (hasDuplicates) {
            log.info("В списке сайтов обнаружены дубликаты.");
        }

        return sites;
    }

    private String getSiteName(String url, List<Site> sites) {
        for (Site site : sites) {
            if (site.getUrl().equals(url)) {
                return site.getName();
            }
        }

        log.info("Сайт с URL '{}' не найден.", url);
        return null;
    }


    public String formatURL(String url) {
        if (url == null || url.isEmpty()) {
            log.info("URL передан пустым");
            return null;
        }

        if (!isValidURL(url)) {
            return null;
        }

        // Если URL начинается с "www.", добавляем "https://"
        if (url.startsWith("www.")) {
            url = "https://" + url;
        }

        // Если URL не содержит протокола, добавляем "https://"
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        // Убираем завершающий символ "/"
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean isValidURL(String url) {
        boolean isGeneralURL = URL_PATTERN.matcher(url).matches();
        boolean isLocalhost = isLocalhostURL(url);

        if (!isGeneralURL && !isLocalhost) {
            log.info("URL имеет неверный формат: {}", url);
        }

        return isGeneralURL || isLocalhost;
    }

    private boolean isLocalhostURL(String url) {
        return LOCALHOST_PATTERN.matcher(url).matches();
    }

    private String extractBaseUrl(String url) {
        int thirdSlashIndex = url.indexOf("/", url.indexOf("//") + 2);
        return thirdSlashIndex == -1 ? url : url.substring(0, thirdSlashIndex);
    }

    public List<String> formattedUrlSites() {
        List<Site> notFormatted = getAvailableSites();

        List<String> formattedUrls = new ArrayList<>();
        for (Site site : notFormatted) {
            String formattedUrl = formatURL(site.getUrl());
            if (formattedUrl != null) {
                formattedUrls.add(extractBaseUrl(formattedUrl));
            }
        }

        return formattedUrls;
    }

    public boolean isUrlInSiteList(String url) {
        List<String> formattedUrls = formattedUrlSites();
        return formattedUrls.contains(url);
    }

    public String validateURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.info("URL передан пустым");
            return null;
        }

        String formattedUrl = formatURL(url);
        String extractedUrl = extractBaseUrl(formattedUrl);

        if (!isUrlInSiteList(extractedUrl)) {
            log.info("Url не найден в списке сайтов: {}", formattedUrl);
            return null;
        }
        return formattedUrl;
    }

}
