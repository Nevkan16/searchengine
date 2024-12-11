package searchengine.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.entity.PageEntity;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
}

