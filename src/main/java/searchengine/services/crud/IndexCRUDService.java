package searchengine.services.crud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexCRUDService {

    private final IndexRepository indexRepository;

    @Transactional
    public void createIndex(PageEntity page, LemmaEntity lemma, float rank) {
        if (page == null) {
            return;
        }
        if (lemma == null) {
            return;
        }
        if (!indexRepository.existsByPageAndLemma(page, lemma)) {
            if (lemma.getSite() == null) {
                return;
            }
            IndexEntity index = new IndexEntity();
            populateIndexEntity(index, page, lemma, rank);
            indexRepository.save(index);
        }
    }

    private void populateIndexEntity(IndexEntity index, PageEntity page, LemmaEntity lemma, float rank) {
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(rank);
    }
}
