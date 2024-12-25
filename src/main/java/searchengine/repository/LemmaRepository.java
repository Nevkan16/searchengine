package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {
    Optional<LemmaEntity> findByLemmaAndSite(String lemma, SiteEntity site);

    @Query("SELECT COUNT(DISTINCT i.page) FROM IndexEntity i WHERE i.lemma = :lemma")
    int calculateFrequency(@Param("lemma") LemmaEntity lemma);
}

