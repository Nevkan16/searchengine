package searchengine.services.crud;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PageCRUDService {

    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaCRUDService lemmaCRUDService;

    public PageEntity createPageEntity(SiteEntity site, String path, int code, String content) {
        PageEntity pageEntity = new PageEntity();
        pageEntity.setSite(site);
        pageEntity.setPath(path);
        pageEntity.setCode(code);
        pageEntity.setContent(content);
        return pageEntity;
    }

    @Transactional
    public PageEntity createPageIfNotExists(SiteEntity site, String path, int code, String content) {
        if (site == null) {
            return null;
        }

        try {
            Optional<PageEntity> existingPage = pageRepository.findBySiteAndPath(site, path);
            if (existingPage.isPresent()) {
                log.info("Страница уже существует по пути: {} для сайта: {}", path, site.getName());
                return existingPage.get();
            }

            PageEntity pageEntity = createPageEntity(site, path, code, content);
            pageRepository.save(pageEntity);
            site.setStatusTime(LocalDateTime.now());
            log.info("Страница создана по пути: {} для сайта: {}. Текущее время: {}",
                    path, site.getName(), site.getStatusTime());

            return pageEntity;
        } catch (Exception e) {
            log.error("Ошибка при проверке и сохранении страницы для пути: {} и сайта: {}. Сообщение: {}",
                    path, site.getName(), e.getMessage());
            return null;
        }
    }

    public Optional<PageEntity> getPageByPath(String path) {
        return pageRepository.findByPath(path);
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
}
