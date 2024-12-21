package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;

@Service
@RequiredArgsConstructor
public class IndexCRUDService {

    private final IndexRepository indexRepository;

    @Transactional
    public void createIndex(PageEntity page, LemmaEntity lemma, float rank) {
        IndexEntity index = new IndexEntity();
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(rank);
        indexRepository.save(index);
    }

    @Transactional
    public void deleteIndex(IndexEntity index) {
        indexRepository.delete(index);
    }
}

