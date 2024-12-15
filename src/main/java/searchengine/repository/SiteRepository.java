package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteEntity;

import java.util.List;

public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
    SiteEntity findByUrl(String url);

    List<SiteEntity> findByStatus(SiteEntity.Status status);
}
