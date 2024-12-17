package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

@Service
@RequiredArgsConstructor
public class PageDataService {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    @Transactional
    public void addPage(SiteEntity site, String path, int code, String content) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(site);
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setContent(content);

        // Сохраняем страницу в базе данных
        pageRepository.save(pageEntity);
    }

    // Метод для получения URL сайта по ID
    public String getSiteUrlById(Long id) {
        SiteEntity site = siteRepository.findById(id).orElse(null);
        if (site != null) {
            return site.getUrl();
        } else {
            throw new IllegalArgumentException("Site not found");
        }
    }

    public SiteEntity getSiteEntityByUrl(String url) {
        return siteRepository.findByUrl(url)
                .orElseThrow(() -> new IllegalArgumentException("Site not found for URL: " + url));
    }

}
