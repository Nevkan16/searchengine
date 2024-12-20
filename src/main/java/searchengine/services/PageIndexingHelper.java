package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

@Component
@RequiredArgsConstructor
public class PageIndexingHelper {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    public SiteEntity getSiteByUrl(String url) {
//        String siteUrl = extractSiteUrl(url); // Метод для получения базового URL сайта
        return siteRepository.findByUrl(url)
                .orElseThrow(() -> new IllegalArgumentException("Сайт для URL " + url + " не найден."));
    }

    public void deletePageIfExists(String url) {
        pageRepository.findByPath(url).ifPresent(pageRepository::delete);
    }

}
