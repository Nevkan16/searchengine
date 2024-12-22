package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;

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

        // Если URL содержит localhost без порта, добавляем порт из конфигурации
        if (isLocalhostURL(url) && !url.contains(":")) {
            url = url + ":" + serverPort;
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
}
