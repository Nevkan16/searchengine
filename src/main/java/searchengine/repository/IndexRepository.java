package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;
import java.util.Optional;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {
    boolean existsByPageAndLemma(PageEntity page, LemmaEntity lemma);
    List<IndexEntity> findAllByPageId(int pageId);

    List<IndexEntity> findByPage(PageEntity page);
    @Query("SELECT i.page FROM IndexEntity i WHERE i.lemma = :lemmaEntity")
    List<PageEntity> findPagesByLemma(@Param("lemmaEntity") LemmaEntity lemmaEntity);

    @Query("SELECT i.rank FROM IndexEntity i WHERE i.page = :page AND i.lemma = :lemma")
    Optional<Float> findRankByPageAndLemma(@Param("page") PageEntity page, @Param("lemma") LemmaEntity lemma);
}
