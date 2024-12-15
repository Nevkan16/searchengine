package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.Optional;

@Service
public class TestService {

    private final DataService dataService;
    private final DummyDataService dummyDataService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public TestService(DataService dataService, DummyDataService dummyDataService, SiteRepository siteRepository, PageRepository pageRepository) {
        this.dataService = dataService;
        this.dummyDataService = dummyDataService;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public void testDummyDataMethods() {
        // Добавляем фиктивные данные
        dummyDataService.addDummyData();

        // Удаляем данные для сайта https://www.lenta.ru
        boolean result = dataService.deleteSiteData("https://www.lenta.ru");
        if (result) {
            System.out.println("Данные сайта https://www.lenta.ru удалены.");
        } else {
            System.out.println("Сайт https://www.lenta.ru не найден.");
        }
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


}

