package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

@Service
public class TestService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;

    public TestService(SiteRepository siteRepository, PageRepository pageRepository, SitesList sitesList) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.sitesList = sitesList;
    }

    public void deleteSiteData(String url) {
        SiteEntity site = siteRepository.findByUrl(url);  // Ищем сайт по URL
        if (site != null) {
            // Удаляем страницы сайта
            pageRepository.deleteBySite(site);
            // Удаляем сам сайт
            siteRepository.delete(site);
        }
    }

    public void testingMethod() {
        for (Site list : sitesList.getSites()) {
            System.out.println(list.getUrl());
        }
        for (Site siteConfig : sitesList.getSites()) {
            System.out.println("Проверяем сайт: " + siteConfig.getUrl());

            // Проверяем, существует ли сайт в базе данных
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl());
            if (siteEntity != null) {
                System.out.println("Найден сайт в БД: " + siteConfig.getUrl() + ". Удаляем его.");

                try {
                    // Удаляем сам сайт
                    siteRepository.delete(siteEntity);
                    System.out.println("Сайт удален: " + siteConfig.getUrl());
                } catch (Exception e) {
                    System.out.println("Ошибка с удалением сайта: " + siteConfig.getUrl());
                    continue;
                }

                try {
                    // Удаляем страницы, связанные с этим сайтом
                    pageRepository.deleteBySite(siteEntity);
                    System.out.println("Страницы удалены для сайта: " + siteConfig.getUrl());
                } catch (Exception e) {
                    System.out.println("Ошибка с удалением страниц для сайта: " + siteConfig.getUrl());
                }
            } else {
                System.out.println("Сайт не найден в БД: " + siteConfig.getUrl());
            }
        }
    }
}

