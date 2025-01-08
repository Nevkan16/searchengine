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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestMethod {
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;

    public void testDeletePage() {
//        deletePageByPath("/basket.html");
        deletePageLemmaByPath("/basket.html");
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
                updateOrDeleteLemma(lemma);
            } else {
                log.warn("Лемма для indexId {} не найдена.", index.getId());
            }
        }
    }

    private void updateOrDeleteLemma(LemmaEntity lemma) {
        int newFrequency = lemma.getFrequency() - 1;
        if (newFrequency > 0) {
            lemma.setFrequency(newFrequency);
            lemmaRepository.save(lemma);
        } else {
            lemmaRepository.delete(lemma);
        }
    }

}
