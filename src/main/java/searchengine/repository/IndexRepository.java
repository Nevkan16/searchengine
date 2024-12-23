package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    boolean existsByPageAndLemma(PageEntity page, LemmaEntity lemma);
}
