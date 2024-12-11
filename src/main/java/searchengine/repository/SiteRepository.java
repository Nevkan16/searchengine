package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.entity.SiteEntity;

import java.util.Optional;

public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
    Optional<SiteEntity> findByUrl(String url);
}
