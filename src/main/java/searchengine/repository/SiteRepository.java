package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<SiteEntity, Long> {
    Optional<SiteEntity> findByUrl(String url);  // Оставить только этот метод

    List<SiteEntity> findByStatus(SiteEntity.Status status);

}

