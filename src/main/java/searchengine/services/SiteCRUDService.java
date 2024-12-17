package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.model.SiteEntity;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SiteCRUDService implements CRUDService<SiteEntity, Long> {

    @Autowired
    private SiteRepository siteRepository;

    @Override
    @Transactional
    public void create() {
        Site site = new Site();
        System.out.println("Создание новой записи о сайте: " + site.getUrl());

        try {
            // Создаем объект SiteEntity для записи в базу данных
            SiteEntity siteEntity = SiteEntity.builder()
                    .url(site.getUrl())
                    .name(site.getName())
                    .status(SiteEntity.Status.INDEXING)
                    .statusTime(LocalDateTime.now())
                    .lastError(null)
                    .build();

            // Сохраняем объект в базе данных
            siteRepository.save(siteEntity);
            System.out.println("Сайт успешно сохранен в БД: " + site.getUrl());
        } catch (Exception e) {
            System.out.println("Ошибка при сохранении сайта в БД: " + e.getMessage());
        }
    }

    @Override
    public List<SiteEntity> getAll() {
        System.out.println("Получение всех сайтов из БД...");
        return siteRepository.findAll();
    }

    @Override
    public SiteEntity getById(Long id) {
        System.out.println("Получение сайта по ID: " + id);
        Optional<SiteEntity> siteEntityOptional = siteRepository.findById(id);
        return siteEntityOptional.orElseThrow(() -> new RuntimeException("Сайт не найден с ID: " + id));
    }

    @Override
    @Transactional
    public void update(Long id, SiteEntity updatedSite) {
        System.out.println("Обновление данных сайта с ID: " + id);
        SiteEntity existingSite = getById(id);

        existingSite.setUrl(updatedSite.getUrl());
        existingSite.setName(updatedSite.getName());
        existingSite.setStatus(updatedSite.getStatus());
        existingSite.setStatusTime(updatedSite.getStatusTime());
        existingSite.setLastError(updatedSite.getLastError());

        siteRepository.save(existingSite);
        System.out.println("Данные сайта обновлены: " + existingSite.getUrl());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        System.out.println("Удаление сайта с ID: " + id);
        SiteEntity existingSite = getById(id);
        siteRepository.delete(existingSite);
        System.out.println("Сайт удален: " + existingSite.getUrl());
    }


}

