package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;

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
            site.setStatusTime(LocalDateTime.now());
            log.info("Текущее время " + site.getStatusTime());

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

    public SiteEntity getSiteEntityByUrl(String url) {
        return siteRepository.findByUrl(url)
                .orElseGet(() -> {
                    log.error("Site not found for URL: {}", url);
                    return null;
                });
    }

    public void deleteAllPages () {
        try {
            pageRepository.deleteAll(); // Удалить все записи
        } catch (Exception e) {
            log.info("Ошибка удаления страниц deleteAll");
        }
    }

    @Transactional
    public void updateSiteError(SiteEntity siteEntity, String errorMessage) {
        try {
            siteEntity.setLastError(errorMessage);
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
            log.info("Updated lastError for site: {}", siteEntity.getUrl());
        } catch (Exception e) {
            log.error("Failed to update lastError for site: {}", siteEntity.getUrl(), e);
        }
    }

}
