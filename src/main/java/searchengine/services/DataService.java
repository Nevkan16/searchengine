package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

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

    public List<Site> getAllSites() {
        List<Site> allSites = sitesList.getSites();

        if (allSites == null || allSites.isEmpty()) {
            return Collections.emptyList(); // Возвращаем пустой список, если сайтов нет
        }

        Set<String> uniqueUrls = new HashSet<>(); // Для отслеживания уникальных URL
        List<Site> validSites = new ArrayList<>();

        for (Site site : allSites) {
            if (site != null && site.getUrl() != null && !site.getUrl().isEmpty() && site.getName() != null) {
                if (uniqueUrls.add(site.getUrl())) { // Добавляем только уникальные URL
                    validSites.add(site);
                }
            }
        }
        return validSites;
    }

    @Transactional
    public void saveOrUpdateSite(Site site, Long siteId) {
        System.out.println(siteId == null
                ? "Создание новой записи о сайте: " + site.getUrl()
                : "Обновление записи о сайте: " + site.getUrl() + " с id: " + siteId);

        SiteEntity siteEntity = findOrCreateSiteEntity(site, siteId);

        if (siteEntity == null) {
            System.out.println("Сайт уже существует в БД: " + site.getUrl());
            return;
        }

        try {
            populateSiteEntity(siteEntity, site);
            siteRepository.save(siteEntity);
            System.out.println("Сайт успешно " + (siteId == null ? "создан" : "обновлён") + " в БД: " + site.getUrl());

        } catch (Exception e) {
            System.out.println("Ошибка при сохранении сайта в БД: " + e.getMessage());
        }
    }

    @Transactional
    public void resetIncrement() {
        resetAutoIncrement("page");
        resetAutoIncrement("site");
    }

    // В классе SiteService
    public void updateSiteStatusToIndexed(String siteUrl) {
        Optional<SiteEntity> siteEntity = siteRepository.findByUrl(siteUrl); // Получаем сайт по URL
        if (siteEntity.isPresent()) {
            SiteEntity indexedSite = siteEntity.get();
            indexedSite.setStatus(SiteEntity.Status.INDEXED);
            indexedSite.setStatusTime(LocalDateTime.now());
            siteRepository.save(indexedSite);
            System.out.println("Site status updated to INDEXED: " + siteUrl);
        } else {
            System.out.println("Site not found: " + siteUrl);
        }
    }


    private SiteEntity findOrCreateSiteEntity(Site site, Long siteId) {
        SiteEntity siteEntity = siteId != null
                ? siteRepository.findById(siteId).orElse(null)
                : null;

        if (siteEntity == null && siteRepository.findByUrl(site.getUrl()).isPresent()) {
            // Если сайт не найден по ID и уже существует по URL
            return null;
        }

        return siteEntity != null ? siteEntity : new SiteEntity();
    }

    private void populateSiteEntity(SiteEntity siteEntity, Site site) {
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(SiteEntity.Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setLastError(null);
    }

    public void validateAndUpdateSiteStatus(SiteEntity siteEntity) {
        String validationError = validateSite(siteEntity.getUrl());
        if (validationError != null) {
            // Если валидация провалилась
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setLastError(validationError);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            System.out.println("Валидация сайта не пройдена: " + siteEntity.getUrl() + " (" + validationError + ")");
        } else {
            System.out.println("Сайт успешно прошёл валидацию: " + siteEntity.getUrl());
        }
    }

    public void setLastError(SiteEntity siteEntity) {

    }


    @Transactional
    public void deleteSitesNotInConfig(List<Site> configuredSites) {
        System.out.println("Удаление сайтов, отсутствующих в конфигурации...");

        // Получаем все сайты из базы данных
        List<SiteEntity> allSitesInDb = siteRepository.findAll();

        // Собираем URL сайтов из конфигурации
        Set<String> configuredUrls = new HashSet<>();
        for (Site site : configuredSites) {
            configuredUrls.add(site.getUrl());
        }

        // Фильтруем сайты, которых нет в конфигурации
        List<SiteEntity> sitesToDelete = new ArrayList<>();
        for (SiteEntity siteEntity : allSitesInDb) {
            if (!configuredUrls.contains(siteEntity.getUrl())) {
                sitesToDelete.add(siteEntity);
            }
        }

        // Удаляем сайты из базы данных
        for (SiteEntity siteToDelete : sitesToDelete) {
            System.out.println("Удаляем сайт: " + siteToDelete.getUrl());
            pageRepository.deleteBySite(siteToDelete);
            siteRepository.delete(siteToDelete);
        }

        System.out.println("Удаление завершено. Удалено сайтов: " + sitesToDelete.size());
    }

    @Transactional
    public void deleteAllSites() {
        System.out.println("Удаление всех сайтов из базы данных...");

        pageRepository.deleteAll();
        siteRepository.deleteAll();

        System.out.println("Все сайты удалены из базы данных.");
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
    public void deleteSiteData() {
        System.out.println("Удаление старых данных...");
        List<Site> allSites = getAllSites(); // Получаем проверенный список сайтов
        for (Site siteConfig : allSites) {
            System.out.println("Проверяем сайт: " + siteConfig.getUrl());
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl()).orElseThrow();

            System.out.println("Найден сайт в БД: " + siteConfig.getUrl() + ". Удаляем данные.");
            // Удаление связанных данных
            pageRepository.deleteBySite(siteEntity);
            System.out.println("Связанные страницы удалены для сайта: " + siteConfig.getUrl());

            siteRepository.delete(siteEntity);
            System.out.println("Сайт удален из БД: " + siteConfig.getUrl());

            // Сброс автоинкремента
            resetAutoIncrement("page");
            resetAutoIncrement("site");

        }
    }

    private String validateSite(String siteUrl) {
        try {
            int timeOutInt = 3000;
            Document doc = Jsoup.connect(siteUrl).timeout(timeOutInt).get();
            Elements links = doc.select("a[href]");

            if (links.isEmpty()) {
                return "No links found";
            }
            return null; // Сайт валиден
        } catch (IOException e) {
            return "Timeout or connection error";
        }
    }

    public List<String> getSitesForIndexing() {
        List<SiteEntity> indexingSites = siteRepository.findByStatus(SiteEntity.Status.INDEXING);
        if (indexingSites.isEmpty()) {
            System.out.println("No sites with status INDEXING found.");
            return new ArrayList<>();
        }
        List<String> sitesForIndexing = new ArrayList<>();
        for (SiteEntity siteEntity : indexingSites) {
            sitesForIndexing.add(siteEntity.getUrl());
        }
        return sitesForIndexing;
    }

    public void handleManualStop() {
        for (SiteEntity siteEntity : siteRepository.findAll()) {
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError("Индексация остановлена пользователем");
            siteRepository.save(siteEntity);
        }
    }
}
