package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigUtil {

    private final SitesList sitesList;

    // Порт из конфигурации
    @Value("${server.port}")
    private int serverPort;

    // получаем имя сайта из списка сайтов (без дубликатов)
    public String getSiteNameFromConfig(String url) {
        List<Site> availableSites = getAvailableSites();

        if (availableSites.isEmpty()) {
            log.info("Список сайтов пуст.");
            return null;
        }

        return getSiteName(url, availableSites);
    }

    // получаем список сайтов без дубликатов
    public List<Site> getAvailableSites() {
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

    // получаем имя из объекта Site
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
            log.info("URL передан пустым.");
            return null;
        }

        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            URI uri = new URI(url);

            // Составляем новый URL без завершающего "/"
            return uri.normalize().toString().replaceAll("/$", "");
        } catch (URISyntaxException e) {
            log.info("Некорректный URL: {}", url);
            return null;
        }
    }

    // Получаем список форматированных url из конфиг файла без дубликатов
    public List<String> formattedUrlSites() {
        List<Site> notFormatted = getAvailableSites();

        List<String> formattedUrls = new ArrayList<>();
        for (Site site : notFormatted) {
            String formattedUrl = formatURL(site.getUrl());
            if (formattedUrl != null) {
                formattedUrls.add(HtmlLoaderUtil.getSchemeBaseUrl(formattedUrl));
            }
        }

        return formattedUrls;
    }

    // Проверяем содержится ли в списке форматированных url переданный url
    public boolean isUrlInSiteList(String url) {
        List<String> formattedUrls = formattedUrlSites();
        return formattedUrls.contains(url);
    }

    // Валидируем переданный Url (форматированный url сравнивается с форматированными именамим из списка)
    public String validateURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.info("URL передан пустым.");
            return null;
        }

        String formattedUrl = formatURL(url);
        String extractedUrl = HtmlLoaderUtil.getSchemeBaseUrl(formattedUrl);

        if (!isUrlInSiteList(extractedUrl)) {
            log.info("URL не найден в списке сайтов: {}", formattedUrl);
            return null;
        }
        log.info("URL найден в списке сайтов: {}", extractedUrl);
        return formattedUrl;
    }
}
