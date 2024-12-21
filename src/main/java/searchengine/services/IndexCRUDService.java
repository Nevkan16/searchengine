package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.repository.IndexRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IndexCRUDService {

    private final IndexRepository indexRepository;

    @Transactional
    public void createIndex(PageEntity page, LemmaEntity lemma, float rank) {
        IndexEntity index = new IndexEntity();
        populateIndexEntity(index, page, lemma, rank);
        indexRepository.save(index);
    }

    @Transactional
    public Optional<IndexEntity> updateIndex(Integer id, PageEntity page, LemmaEntity lemma, float rank) {
        return indexRepository.findById(id).map(index -> {
            populateIndexEntity(index, page, lemma, rank);
            return indexRepository.save(index);
        });
    }

    @Transactional
    public void deleteIndex(IndexEntity index) {
        indexRepository.delete(index);
    }

    @Transactional
    public Optional<IndexEntity> findIndexById(Integer id) {
        return indexRepository.findById(id);
    }

    @Transactional
    public Iterable<IndexEntity> findAllIndexes() {
        return indexRepository.findAll();
    }

    private void populateIndexEntity(IndexEntity index, PageEntity page, LemmaEntity lemma, float rank) {
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(rank);
    }
}
