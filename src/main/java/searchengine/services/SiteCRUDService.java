package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.*;
@Slf4j
@Service // сервисный слой, регистрирует класс как bean (объект управляемый контейнером Spring)
public class SiteCRUDService {

    @PersistenceContext // внедрение Entity Manager в компоненты приложения
    private EntityManager entityManager;

    @Autowired // автоматическое внедрение зависимостей
    private SiteRepository siteRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private SitesList sitesList;


    public List<Site> getAllSites() {
        List<Site> allSites = sitesList.getSites();
        if (allSites == null || allSites.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> uniqueUrls = new HashSet<>();
        List<Site> validSites = new ArrayList<>();
        for (Site site : allSites) {
            if (site != null && site.getUrl() != null && !site.getUrl().isEmpty() && site.getName() != null) {
                if (uniqueUrls.add(site.getUrl())) {
                    validSites.add(site);
                }
            }
        }
        return validSites;
    }

    // Создание нового сайта
    @Transactional
    public void createSite(Site site) {
        log.info("Создание новой записи о сайте: " + site.getUrl());
        SiteEntity siteEntity = new SiteEntity();
        populateSiteEntity(siteEntity, site);
        siteRepository.save(siteEntity);
    }

    // Обновление информации о сайте
    @Transactional
    public void updateSite(Long siteId, Site site) {
        System.out.println("Обновление записи о сайте: " + site.getUrl() + " с id: " + siteId);
        SiteEntity siteEntity = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Сайт не найден для обновления"));
        populateSiteEntity(siteEntity, site);
        siteRepository.save(siteEntity);
    }

    // Получение сайта по ID
    public Optional<SiteEntity> getSiteById(Long siteId) {
        return siteRepository.findById(siteId);
    }

    // Удаление сайта по ID
    @Transactional
    public void deleteSite(Long siteId) {
        SiteEntity siteEntity = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Сайт не найден для удаления"));
        siteRepository.delete(siteEntity);
       log.info("Сайт удалён: " + siteEntity.getUrl());
    }

    @Transactional
    public void resetIncrement() {
        resetAutoIncrement("page");
        resetAutoIncrement("site");
        resetAutoIncrement("index_table");
        resetAutoIncrement("lemma");
    }

    // В классе SiteIndexingService
    public void updateSiteStatusToIndexed(String siteUrl) {
        SiteEntity siteEntity = siteRepository.findByUrl(siteUrl)
                .orElseThrow(() -> new IllegalArgumentException("Сайт не найден: " + siteUrl));
        siteEntity.setStatus(SiteEntity.Status.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        log.info("Статус сайта обновлён на INDEXED: " + siteUrl);
    }

    private void populateSiteEntity(SiteEntity siteEntity, Site site) {
        siteEntity.setUrl(site.getUrl());
        siteEntity.setName(site.getName());
        siteEntity.setStatus(SiteEntity.Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteEntity.setLastError(null);
    }

    @Transactional
    public void deleteSitesNotInConfig(List<Site> configuredSites) {
        log.info("Удаление сайтов, отсутствующих в конфигурации...");
        List<SiteEntity> allSitesInDb = siteRepository.findAll();
        Set<String> configuredUrls = new HashSet<>();
        for (Site site : configuredSites) {
            configuredUrls.add(site.getUrl());
        }

        List<SiteEntity> sitesToDelete = new ArrayList<>();
        for (SiteEntity siteEntity : allSitesInDb) {
            if (!configuredUrls.contains(siteEntity.getUrl())) {
                sitesToDelete.add(siteEntity);
            }
        }

        for (SiteEntity siteToDelete : sitesToDelete) {
            log.info("Удаляем сайт: " + siteToDelete.getUrl());
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
        lemmaRepository.deleteAll();
        indexRepository.deleteAll();

        System.out.println("Все сайты удалены из базы данных.");
    }


    private void resetAutoIncrement(String tableName) {
        try {
            entityManager.createNativeQuery("ALTER TABLE " + tableName + " AUTO_INCREMENT = 1").executeUpdate();
            log.info("Автоинкремент сброшен для таблицы: " + tableName);
        } catch (Exception e) {
            log.info("Ошибка при сбросе автоинкремента для таблицы " + tableName + ": " + e.getMessage());
        }
    }

    @Transactional
    public void deleteSiteData() {
        log.info("Удаление старых данных...");
        List<Site> allSites = getAllSites(); // Получаем проверенный список сайтов
        for (Site siteConfig : allSites) {
            log.info("Проверяем сайт: " + siteConfig.getUrl());
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl()).orElseThrow();

            log.info("Найден сайт в БД: " + siteConfig.getUrl() + ". Удаляем данные.");
            // Удаление связанных данных
            pageRepository.deleteBySite(siteEntity);
            log.info("Связанные страницы удалены для сайта: " + siteConfig.getUrl());

            siteRepository.delete(siteEntity);
            log.info("Сайт удален из БД: " + siteConfig.getUrl());

            // Сброс автоинкремента
            resetAutoIncrement("page");
            resetAutoIncrement("site");

        }
    }

    public List<String> getSitesForIndexing() {
        List<SiteEntity> indexingSites = siteRepository.findByStatus(SiteEntity.Status.INDEXING);
        if (indexingSites.isEmpty()) {
            log.info("No sites with status INDEXING found.");
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

    public SiteEntity getSiteByUrl(String url) {
        return siteRepository.findByUrl(url).orElse(null);
    }

    public boolean isDatabaseEmpty() {
        long count = siteRepository.count();
        if (count == 0) {
            log.info("База данных пуста.");
            return true;
        }
        return false;
    }


}
