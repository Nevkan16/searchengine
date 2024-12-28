package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;
import java.util.Optional;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    boolean existsByPageAndLemma(PageEntity page, LemmaEntity lemma);
    List<IndexEntity> findAllByPageId(int pageId);

    List<IndexEntity> findByPage(PageEntity page);

    Optional<IndexEntity> findByPageAndLemma(PageEntity page, LemmaEntity lemma);
}
