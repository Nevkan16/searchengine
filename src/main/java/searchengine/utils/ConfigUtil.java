package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;

import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigUtil {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://)?(www\\.)?[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(/.*)?$");
    private final SitesList sitesList;
    public String getSiteNameFromConfig(String url) {
        if (sitesList == null || sitesList.getSites().isEmpty()) {
            log.info("Список сайтов пуст или не инициализирован.");
            return null; // Возвращаем null, если список пуст
        }

        String siteName = null;
        boolean isDuplicate = false;

        for (Site site : sitesList.getSites()) {
            if (site.getUrl().equals(url)) {
                if (siteName == null) {
                    siteName = site.getName();
                } else {
                    isDuplicate = true;
                    break;
                }
            }
        }

        if (isDuplicate) {
            log.info("Обнаружен дубль URL: {}", url);
        }

        return siteName;
    }

    public String formatURL(String url) {
        if (url == null || url.isEmpty()) {
            log.info("URL передан пустым");
            return null; // Возвращаем null, если URL некорректен
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
        boolean isValid = URL_PATTERN.matcher(url).matches();
        if (!isValid) {
            log.info("URL имеет неверный формат: {}", url);
        }
        return isValid;
    }
}
