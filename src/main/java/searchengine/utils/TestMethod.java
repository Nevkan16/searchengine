package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;

import javax.transaction.Transactional;
import java.net.URI;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestMethod {
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;

    @Transactional
    public void deletePageByUrl(String url) {
        try {
            // Извлекаем путь из URL
            URI uri = new URI(url);
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                log.error("Некорректный URL: {}", url);
                return;
            }

            // Найти страницу по пути
            pageRepository.findByPath(path).ifPresent(page -> {
                int pageId = page.getId();

                // Удаляем записи из index_table, связанные с page_id
                List<IndexEntity> indexes = indexRepository.findAllByPageId(pageId);
                for (IndexEntity index : indexes) {
                    LemmaEntity lemma = index.getLemma();
                    indexRepository.delete(index);

                    // Уменьшаем частоту леммы
                    int newFrequency = lemma.getFrequency() - 1;
                    if (newFrequency > 0) {
                        lemma.setFrequency(newFrequency);
                        lemmaRepository.save(lemma);
                    } else {
                        lemmaRepository.delete(lemma); // Удаляем лемму, если частота стала 0
                    }
                }

                // Удаляем страницу
                pageRepository.delete(page);
                log.info("Страница с URL {} и связанные данные успешно удалены.", url);
            });

        } catch (Exception e) {
            log.error("Ошибка при удалении страницы с URL: {}", url, e);
        }
    }

    public void testDeletePage() {
        deletePageByPath("/basket.html");
    }

    @Transactional
    public void deletePageByPath(String path) {
        pageRepository.findByPath(path).ifPresent(page -> {
            int pageId = page.getId();

            // Удаляем записи из index_table, связанные с page_id
            List<IndexEntity> indexes = indexRepository.findAllByPageId(pageId);
            for (IndexEntity index : indexes) {
                LemmaEntity lemma = index.getLemma();
                indexRepository.delete(index);

                // Уменьшаем частоту леммы
                int newFrequency = lemma.getFrequency() - 1;
                if (newFrequency > 0) {
                    lemma.setFrequency(newFrequency);
                    lemmaRepository.save(lemma);
                } else {
                    lemmaRepository.delete(lemma); // Удаляем лемму, если частота стала 0
                }
            }

            // Удаляем страницу
            pageRepository.delete(page);
            log.info("Страница с path {} и связанные данные успешно удалены.", path);
        });
    }
}
