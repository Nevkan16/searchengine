package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    void deleteBySite(SiteEntity site);

    boolean existsByPath(String linkHref);

    Optional<PageEntity> findByPath(String url);

    Optional<PageEntity> findBySiteAndPath(SiteEntity site, String url);
    List<PageEntity> findBySiteId(Long siteId);
}
