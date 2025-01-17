package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    void deleteBySite(SiteEntity site);

    Optional<PageEntity> findByPath(String url);

    List<PageEntity> findBySiteId(Long siteId);

    Optional<PageEntity> findBySiteAndPath(SiteEntity site, String path);
}
