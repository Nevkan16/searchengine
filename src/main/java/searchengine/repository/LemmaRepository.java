package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    Optional<LemmaEntity> findByLemmaAndSite(String lemma, SiteEntity site);
    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma IN :lemmas ORDER BY l.frequency ASC")
    List<LemmaEntity> findByLemmaInOrderByFrequencyAsc(@Param("lemmas") List<String> lemmas);
    List<LemmaEntity> findByLemma(String lemmaName);
}

