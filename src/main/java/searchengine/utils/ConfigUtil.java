package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.config.SitesList;
@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigUtil {
    private final SitesList sitesList;
    public String getSiteNameFromConfig(String url) {
        if (sitesList == null || sitesList.getSites().isEmpty()) {
            log.info("Список сайтов пуст или не инициализирован.");
        }

        String siteName = null;
        boolean isDuplicate = false;

        assert sitesList != null;
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
            log.info("Обнаружен дубль URL: " + url);
        }

        return siteName;
    }
}
