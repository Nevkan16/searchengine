package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
@Slf4j
@Service
@RequiredArgsConstructor
public class PageDataService {
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    @Transactional
    public void addPage(SiteEntity site, String path, int code, String content) {
        try {

            PageEntity pageEntity = new PageEntity();
            pageEntity.setSite(site);
            pageEntity.setPath(path);
            pageEntity.setCode(code);
            pageEntity.setContent(content);

            if (!pageRepository.existsByPath(path)) {
                // Сохраняем страницу в базе данных
                pageRepository.save(pageEntity);
            } else {
                log.info("Страница уже существует");
            }
        } catch (Exception e) {
            log.info("Ошибка при сохранении страницы");
        }
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
