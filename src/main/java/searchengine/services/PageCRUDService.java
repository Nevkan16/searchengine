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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageCRUDService {

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
            log.info("Текущее время: {}", site.getStatusTime());

            if (!pageRepository.existsByPath(path)) {
                pageRepository.save(pageEntity); // Сохраняем страницу в базе данных
            } else {
                log.info("Страница уже существует");
            }
        } catch (Exception e) {
            log.error("Ошибка при сохранении страницы: {}", e.getMessage()); // Краткий лог
        }
    }


    public Optional<PageEntity> getPageByPath(String path) {
        return pageRepository.findByPath(path);
    }

    public List<PageEntity> getAllPages() {
        return pageRepository.findAll();
    }

    @Transactional
    public void deletePageByPath(String path) {
        try {
            pageRepository.findByPath(path).ifPresent(pageRepository::delete);
        } catch (Exception e) {
            log.error("Ошибка при удалении страницы по пути: {}", path, e);
        }
    }

    @Transactional
    public void deleteAllPages() {
        try {
            pageRepository.deleteAll();
        } catch (Exception e) {
            log.error("Ошибка удаления всех страниц", e);
        }
    }

    @Transactional
    public void updatePageContent(String path, String newContent) {
        try {
            pageRepository.findByPath(path).ifPresent(page -> {
                page.setContent(newContent);
                pageRepository.save(page);
                log.info("Обновлено содержимое страницы: {}", path);
            });
        } catch (Exception e) {
            log.error("Ошибка при обновлении содержимого страницы: {}", path, e);
        }
    }

    public SiteEntity getSiteEntityByUrl(String url) {
        return siteRepository.findByUrl(url)
                .orElseGet(() -> {
                    log.error("Сайт не найден по URL: {}", url);
                    return null;
                });
    }

    @Transactional
    public void updateSiteError(SiteEntity siteEntity, String errorMessage) {
        try {
            siteEntity.setLastError(errorMessage);
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
            log.info("Обновлена ошибка для сайта: {}", siteEntity.getUrl());
        } catch (Exception e) {
            log.error("Ошибка при обновлении ошибки для сайта: {}", siteEntity.getUrl(), e);
        }
    }
}
