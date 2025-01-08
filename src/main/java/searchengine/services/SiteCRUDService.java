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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
@Slf4j
@RequiredArgsConstructor
@Service // сервисный слой, регистрирует класс как bean (объект управляемый контейнером Spring)
public class SiteCRUDService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesList;
    private final ConfigUtil configUtil;


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

    @Transactional
    public void updateSite(Long siteId, Site site) {
        log.info("Обновление записи о сайте: {}, с id {}", site.getUrl(), siteId);
        SiteEntity siteEntity = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException("Сайт не найден для обновления"));
        populateSiteEntity(siteEntity, site);
        siteRepository.save(siteEntity);
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

        log.info("Удаление завершено. Удалено сайтов: {}", sitesToDelete.size());
    }

    @Transactional
    public void deleteAllSites() {
       log.info("Удаление всех сайтов из базы данных...");

        pageRepository.deleteAll();
        siteRepository.deleteAll();
        lemmaRepository.deleteAll();
        indexRepository.deleteAll();

        log.info("Все сайты удалены из базы данных.");
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
}
