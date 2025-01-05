package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.constants.ErrorMessages;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.ConfigUtil;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.*;
@Slf4j
@RequiredArgsConstructor
@Service // сервисный слой, регистрирует класс как bean (объект управляемый контейнером Spring)
public class SiteCRUDService {

    @PersistenceContext // внедрение Entity Manager в компоненты приложения
    private EntityManager entityManager;

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesList;
    private final ConfigUtil configUtil;


    public List<Site> getAllSites() {
        List<Site> allSites = sitesList.getSites();
        if (allSites == null || allSites.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> uniqueUrls = new HashSet<>();
        List<Site> validSites = new ArrayList<>();

        for (Site site : allSites) {
            if (site != null && site.getUrl() != null && !site.getUrl().isEmpty() && site.getName() != null) {
                String formattedUrl = configUtil.formatURL(site.getUrl());
                if (uniqueUrls.add(formattedUrl)) {
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

    SiteEntity createSiteIfNotExist(String url) {
        ConfigUtil configUtil = new ConfigUtil(sitesList);
        String siteName = configUtil.getSiteNameFromConfig(url);

        if (siteName == null) {
            log.error("Сайт не найден в файле конфигурации: {}", url);
            return null;
        }

        Site newSite = new Site();
        String formattedUrl = configUtil.formatURL(url);
        if (formattedUrl == null) {
            log.error("URL не может быть отформатирован: {}", url);
            return null;
        }
        newSite.setUrl(formattedUrl);
        newSite.setName(siteName);

        createSite(newSite);

        SiteEntity siteEntity = getSiteByUrl(url);
        if (siteEntity == null) {
            log.error("Не удалось создать новый сайт с URL: {}", url);
            return null;
        }

        return siteEntity;
    }

    @Transactional
    public void updateLastError(SiteEntity siteEntity, String errorMessage) {
        if (siteEntity == null) {
            return;
        }
        siteEntity.setLastError(errorMessage);
        siteEntity.setStatusTime(LocalDateTime.now());
        try {
            siteRepository.save(siteEntity);
        } catch (Exception e) {
            log.info("Ошибка при обновлении ошибки для сайта: {}", siteEntity.getUrl());
        }
    }

    @Transactional
    public void updateSiteError(SiteEntity siteEntity, String errorMessage) {
        if (siteEntity == null) {
            return;
        }

        siteEntity.setLastError(errorMessage);
        if (siteEntity.getPages() == null) {
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            log.info("Установлен статус FAILED для сайта: {}", siteEntity.getUrl());
        }

        siteEntity.setStatusTime(LocalDateTime.now());

        try {
            siteRepository.save(siteEntity);
        } catch (Exception e) {
            log.info("Ошибка при обновлении ошибки для сайта: {}", siteEntity.getUrl());
            log.error("Подробности ошибки: ", e);
        }
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
    public SiteEntity getSiteById(Long siteId) {
        return siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Site not found"));
    }

    // Удаление сайта по ID
    @Transactional
    public void deleteSite(Long siteId) {
        SiteEntity siteEntity = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Сайт не найден для удаления"));
        siteRepository.delete(siteEntity);
       log.info("Сайт удалён: " + siteEntity.getUrl());
    }


    // В классе SiteIndexingService
    public void updateSiteStatusToIndexed(String siteUrl) {
        SiteEntity siteEntity = siteRepository.findByUrl(siteUrl)
                .orElseThrow(() -> new IllegalArgumentException("Сайт не найден: " + siteUrl));

        List<PageEntity> pages = pageRepository.findBySiteId(siteEntity.getId());

        if (pages.isEmpty()) {
            siteEntity.setStatus(SiteEntity.Status.FAILED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setLastError(ErrorMessages.SITE_UNAVAILABLE);
            siteRepository.save(siteEntity);
            log.warn("Статус сайта обновлён на FAILED: " + siteUrl);
            return;
        }

        siteEntity.setStatus(SiteEntity.Status.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        log.info("Статус сайта обновлён на INDEXED: " + siteUrl);
    }


    private void populateSiteEntity(SiteEntity siteEntity, Site site) {
        String formattedUrl = configUtil.formatURL(site.getUrl());
        siteEntity.setUrl(formattedUrl);
        siteEntity.setName(site.getName());
        siteEntity.setStatus(SiteEntity.Status.INDEXING);
        log.info("Статус сайта обновлен на INDEXING:");
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
        return siteRepository.findByUrl(url)
                .orElse(null);
    }

    public void isDatabaseEmpty() {
        long count = siteRepository.count();
        if (count == 0) {
            log.info("База данных пуста.");
        }
    }

//    @Async("threadPoolTaskExecutor")
    @Transactional
    public void deleteSiteAsync(String siteUrl) {
        try {
            log.info("Асинхронное удаление сайта: {}", siteUrl);
            SiteEntity siteEntity = siteRepository.findByUrl(siteUrl)
                    .orElseThrow(() -> new IllegalArgumentException("Сайт не найден: " + siteUrl));

            // Удаление связанных данных
            pageRepository.deleteBySite(siteEntity);
            log.info("Связанные страницы удалены для сайта: {}", siteUrl);

            siteRepository.delete(siteEntity);
            log.info("Сайт удалён: {}", siteUrl);
        } catch (Exception e) {
            log.error("Ошибка при асинхронном удалении сайта: {}", siteUrl, e);
        }
    }

    @Transactional
    public void deleteSiteWithTransaction(String siteUrl) {
        try {
            log.info("Удаление сайта с транзакцией: {}", siteUrl);
            SiteEntity siteEntity = siteRepository.findByUrl(siteUrl)
                    .orElseThrow(() -> new IllegalArgumentException("Сайт не найден: " + siteUrl));

            // Удаление связанных данных
            pageRepository.deleteBySite(siteEntity);
            log.info("Связанные страницы удалены для сайта: {}", siteUrl);

            siteRepository.delete(siteEntity);
            log.info("Сайт удалён: {}", siteUrl);
        } catch (Exception e) {
            log.error("Ошибка при удалении сайта: {}", siteUrl, e);
        }
    }
}
