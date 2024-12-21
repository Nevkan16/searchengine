package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

@Service
@RequiredArgsConstructor
public class PageIndexingHelper {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList sitesList;

    public SiteEntity getSiteByUrl(String url) {
        return siteRepository.findByUrl(url).orElse(null);
    }

    public void deletePageIfExists(String url) {
        pageRepository.findByPath(url).ifPresent(pageRepository::delete);
    }

    public String getSiteNameFromConfig(String url) {
        for (Site site : sitesList.getSites()) {
            if (site.getUrl().equals(url)) {
                return site.getName();
            }
        }
        return null;
    }

}
