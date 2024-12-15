package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    void deleteBySite(SiteEntity site);

    boolean existsByPath(String linkHref);
}
