package searchengine.services;

import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@Service // сервисный слой, регистрирует класс как bean (объект управляемый контейнером Spring)
public class DataService {

    @PersistenceContext // внедрение Entity Manager в компоненты приложения
    private EntityManager entityManager;

    @Autowired // автоматическое внедрение зависимостей
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private SitesList sitesList;

    @Transactional
    public void deleteSiteData() {
        List<Site> allSites = sitesList.getSites();
        for (Site siteConfig : allSites) {
            if (siteConfig == null || siteConfig.getUrl() == null || siteConfig.getUrl().isEmpty()) {
                System.out.println("Пропускаем пустой или некорректный сайт в конфигурации.");
                continue;
            }

            System.out.println("Проверяем сайт: " + siteConfig.getUrl());
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl());

            if (siteEntity != null) {
                System.out.println("Найден сайт в БД: " + siteConfig.getUrl() + ". Удаляем данные.");
                // Удаление связанных данных
                pageRepository.deleteBySite(siteEntity);
                System.out.println("Связанные страницы удалены для сайта: " + siteConfig.getUrl());

                siteRepository.delete(siteEntity);
                System.out.println("Сайт удален из БД: " + siteConfig.getUrl());

                // Сброс автоинкремента
                resetAutoIncrement("page");
                resetAutoIncrement("site");
            } else {
                System.out.println("Сайт не найден в БД: " + siteConfig.getUrl());
            }

            // Создание новой записи
            createSiteRecord(siteConfig);
            System.out.println("Создана новая запись для сайта: " + siteConfig.getUrl());
        }
    }

    public void createSiteRecord(Site site) {
        if (site == null || site.getUrl() == null || site.getName() == null) {
            System.out.println("Ошибка: передан пустой сайт для создания записи.");
            return;
        }

        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(SiteEntity.Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setLastError(null);

        try {
            siteRepository.save(siteEntity);
            System.out.println("Сайт успешно сохранен в БД: " + site.getUrl());
        } catch (Exception e) {
            System.out.println("Ошибка при сохранении сайта в БД: " + e.getMessage());
        }
    }


    private void resetAutoIncrement(String tableName) {
        try {
            entityManager.createNativeQuery("ALTER TABLE " + tableName + " AUTO_INCREMENT = 1").executeUpdate();
            System.out.println("Автоинкремент сброшен для таблицы: " + tableName);
        } catch (Exception e) {
            System.out.println("Ошибка при сбросе автоинкремента для таблицы " + tableName + ": " + e.getMessage());
        }
    }

    @Transactional
    public void savePageToDb(String linkHref, Document doc) {
        try {
        // Получаем или создаём сущность PageEntity
        PageEntity pageEntity = new PageEntity();
        pageEntity.setPath(linkHref);
        pageEntity.setContent(doc.html()); // или обработанный контент
        pageEntity.setCode(200); // Код ответа, возможно, стоит передавать как параметр

            // Проверяем на дублирование в базе
            if (!pageRepository.existsByPath(linkHref)) {
                pageRepository.save(pageEntity);
            }
        } catch (Exception e) {
            System.out.println("Failed to save page");
        }
    }

    public void updateSiteStatusTime(SiteEntity siteEntity) {
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    public void finishIndexing(SiteEntity siteEntity, boolean isSuccess) {
        if (isSuccess) {
            siteEntity.setStatus(SiteEntity.Status.INDEXED);
        } else {
            siteEntity.setStatus(SiteEntity.Status.FAILED);
        }
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    public void handleManualStop() {
        for (SiteEntity siteEntity : siteRepository.findByStatus(SiteEntity.Status.INDEXING)) {
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setLastError("Индексация остановлена пользователем");
            siteRepository.save(siteEntity);
        }
    }
}
