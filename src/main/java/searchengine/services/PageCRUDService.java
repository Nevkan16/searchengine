package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageCRUDService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final IndexRepository indexRepository;
    private final LemmaCRUDService lemmaCRUDService;

    // Метод для создания объекта PageEntity
    public PageEntity createPageEntity(SiteEntity site, String path, int code, String content) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(site);
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setContent(content);
        return pageEntity;
    }

    // Остальной код остается без изменений, используем createPageEntity где нужно

    @Transactional
    public void create(SiteEntity site, String path, int code, String content) {
        try {
            PageEntity pageEntity = createPageEntity(site, path, code, content);
            site.setStatusTime(LocalDateTime.now());
            log.info("Текущее время: {}", site.getStatusTime());

            // Сохраняем страницу
            pageRepository.save(pageEntity);
            log.info("Страница успешно создана по пути: {}", path);
        } catch (Exception e) {
            log.error("Ошибка при сохранении страницы: {}", e.getMessage());
        }
    }

    @Transactional
    public PageEntity createPageIfNotExists(SiteEntity site, String path, int code, String content) {
        try {
            // Проверка существования страницы
            Optional<PageEntity> existingPage = pageRepository.findByPath(path);
            if (existingPage.isPresent()) {
                log.info("Страница уже существует по пути: {}", path);
                return existingPage.get();
            }

            // Создание новой страницы
            PageEntity pageEntity = createPageEntity(site, path, code, content);
            pageRepository.save(pageEntity);
            site.setStatusTime(LocalDateTime.now());
            log.info("Страница создана по пути: {}. Текущее время: {}", path, site.getStatusTime());

            return pageEntity;
        } catch (Exception e) {
            log.info("Ошибка при проверке и сохранении страницы: {}. Путь: {}", e.getMessage(), path);
            return null; // Возвращаем null вместо выброса исключения
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
    public void deletePageLemmaByUrl(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                log.warn("Некорректный URL: {}", url);
                return;
            }

            deletePageLemmaByPath(path);
        } catch (Exception e) {
            log.error("Ошибка при обработке URL: {}", url, e);
        }
    }


    @Transactional
    public void deletePageLemmaByPath(String path) {
        pageRepository.findByPath(path).ifPresentOrElse(
                page -> {
                    int pageId = page.getId();
                    deleteIndexesAndLemmasByPageId(pageId);
                    pageRepository.delete(page);
                    log.info("Страница с path {} и связанные данные успешно удалены.", path);
                },
                () -> log.warn("Страница с path {} не найдена.", path)
        );
    }

    private void deleteIndexesAndLemmasByPageId(int pageId) {
        List<IndexEntity> indexes = indexRepository.findAllByPageId(pageId);
        if (indexes.isEmpty()) {
            log.warn("Индексы для pageId {} не найдены.", pageId);
            return;
        }

        for (IndexEntity index : indexes) {
            LemmaEntity lemma = index.getLemma();
            indexRepository.delete(index);
            if (lemma != null) {
                lemmaCRUDService.updateOrDeleteLemma(lemma);
            } else {
                log.warn("Лемма для indexId {} не найдена.", index.getId());
            }
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

    public void deletePageIfExists(String url) {
        pageRepository.findByPath(url).ifPresent(pageRepository::delete);
    }

}
