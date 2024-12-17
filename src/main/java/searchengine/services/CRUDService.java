package searchengine.services;

import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;

import java.util.List;

public interface CRUDService<T, ID> {

    @Transactional
    void create();

    List<T> getAll();
    T getById(ID id);
    void update(ID id, T t);
    void delete(ID id);
}

