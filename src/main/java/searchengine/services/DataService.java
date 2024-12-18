package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
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
    public void deleteSiteByUrl(String siteUrl) {
        System.out.println("Удаление данных для сайта: " + siteUrl);

        Optional<SiteEntity> siteEntityOpt = siteRepository.findByUrl(siteUrl);
        if (siteEntityOpt.isPresent()) {
            SiteEntity siteEntity = siteEntityOpt.get();

            System.out.println("Найден сайт в БД: " + siteUrl + ". Удаляем связанные страницы...");
            pageRepository.deleteBySite(siteEntity);
            System.out.println("Связанные страницы удалены для сайта: " + siteUrl);

            siteRepository.delete(siteEntity);
            System.out.println("Сайт удален из БД: " + siteUrl);

        } else {
            System.out.println("Сайт не найден в БД: " + siteUrl + ". Удаление пропущено.");
        }
    }

    @Transactional
    public void resetIncrement() {
        resetAutoIncrement("page");
        resetAutoIncrement("site");
    }

    // В классе SiteService
    public void updateSiteStatusToIndexed(String siteUrl) {
        Optional<SiteEntity> siteEntity = siteRepository.findSiteByUrl(siteUrl); // Получаем сайт по URL
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


    @Transactional
    public void createSiteRecord(Site site) {
        System.out.println("Создание новой записи о сайте: " + site.getUrl());

        if (siteRepository.findByUrl(site.getUrl()).isPresent()) {
            System.out.println("Сайт уже существует в БД: " + site.getUrl());
            return;
        }

        try {
            SiteEntity siteEntity = SiteEntity.builder()
                    .url(site.getUrl())
                    .name(site.getName())
                    .status(SiteEntity.Status.INDEXING)
                    .statusTime(LocalDateTime.now())
                    .lastError(null)
                    .build();

            siteRepository.save(siteEntity);
            System.out.println("Сайт успешно сохранен в БД: " + site.getUrl());
        } catch (Exception e) {
            System.out.println("Ошибка при сохранении сайта в БД: " + e.getMessage());
        }
    }

    @Transactional
    public void updateSiteRecord(Site site, long siteId) {
        System.out.println("Обновление записи о сайте: " + site.getUrl() + " с id: " + siteId);

        Optional<SiteEntity> existingSiteOpt = siteRepository.findById(siteId);
        if (existingSiteOpt.isPresent()) {
            // Сайт с таким id существует, обновляем его данные
            SiteEntity existingSite = existingSiteOpt.get();

            existingSite.setUrl(site.getUrl());
            existingSite.setName(site.getName());
            existingSite.setStatus(SiteEntity.Status.INDEXING);
            existingSite.setStatusTime(LocalDateTime.now());
            existingSite.setLastError(null);

            siteRepository.save(existingSite);
            System.out.println("Сайт успешно обновлен в БД: " + site.getUrl());
        } else {
            // Сайт с таким id отсутствует, создаем новую запись
            System.out.println("Сайт с id: " + siteId + " отсутствует в БД. Создаем новую запись.");
            createSiteRecord(site);
        }
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

    public List<Site> getValidSites() {
        System.out.println("Проверка сайта на валидность ссылок...");
        List<Site> validSites = new ArrayList<>();

        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            System.out.println("Checking site: " + siteUrl);

            String validationError = validateSite(siteUrl);

            if (validationError == null) {
                validSites.add(site);
                System.out.println("Site is valid: " + siteUrl);
            } else {
                System.out.println("Site is invalid: " + siteUrl + " (" + validationError + ")");
            }
        }

        System.out.println("Total valid sites: " + validSites.size());
        return validSites;
    }

    @Transactional
    public void updateSites() {
        System.out.println("Обновление данных для сайта с ошибками...");
        for (Site site : sitesList.getSites()) {
            String siteUrl = site.getUrl();
            System.out.println("Checking site in DB: " + siteUrl);

            String validationError = validateSite(siteUrl);

            if (validationError != null) {
                updateSiteStatusInDb(site, validationError);
            }
        }
    }

    private void updateSiteStatusInDb(Site site, String lastError) {
        SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl()).orElseThrow();
        siteEntity.setStatus(SiteEntity.Status.FAILED);
        siteEntity.setLastError(lastError);
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
        System.out.println("Updated site in DB: " + site.getUrl() + " (Status: " + SiteEntity.Status.FAILED + ", Error: " + lastError + ")");
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
