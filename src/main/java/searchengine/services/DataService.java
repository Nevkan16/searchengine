package searchengine.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

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

    @Transactional // Spring оборачивает в транзакцию, Атомарность (либо все либо ничего), если искл то откатывает изменения, подерживает работу с субд
    public void deleteSiteData() {
        for (Site siteConfig : sitesList.getSites()) {
            System.out.println("Проверяем сайт: " + siteConfig.getUrl());

            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl());
            if (siteEntity != null) {
                System.out.println("Найден сайт в БД: " + siteConfig.getUrl() + ". Удаляем его.");

                try {
                    pageRepository.deleteBySite(siteEntity);
                    System.out.println("Страницы удалены для сайта: " + siteConfig.getUrl());

                    siteRepository.delete(siteEntity);
                    System.out.println("Сайт удален: " + siteConfig.getUrl());

                    resetAutoIncrement("page");
                    resetAutoIncrement("site");
                } catch (Exception e) {
                    System.out.println("Ошибка при удалении сайта или страниц: " + e.getMessage());
                }
            } else {
                System.out.println("Сайт не найден в БД: " + siteConfig.getUrl());
            }
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
}
