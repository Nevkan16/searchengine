package searchengine.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.model.SiteEntity.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
public class DummyDataService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public DummyDataService(SiteRepository siteRepository, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional
    public void addDummyData() {
        // Создаем фиктивные сайты
        SiteEntity lentaSite = new SiteEntity();
        lentaSite.setUrl("https://www.lenta.ru");
        lentaSite.setName("Lenta");
        lentaSite.setStatus(Status.INDEXING);
        lentaSite.setStatusTime(LocalDateTime.now());
        lentaSite.setLastError(null);

        SiteEntity playbackSite = new SiteEntity();
        playbackSite.setUrl("https://www.playback.ru");
        playbackSite.setName("Playback");
        playbackSite.setStatus(Status.INDEXING);
        playbackSite.setStatusTime(LocalDateTime.now());
        playbackSite.setLastError(null);

        // Сохраняем сайты
        siteRepository.saveAll(Arrays.asList(lentaSite, playbackSite));

        // Добавляем фиктивные страницы для каждого сайта
        PageEntity lentaPage1 = new PageEntity();
        lentaPage1.setSite(lentaSite);
        lentaPage1.setPath("/news");
        lentaPage1.setCode(200);
        lentaPage1.setContent("<html><body>News content for lenta.ru</body></html>");

        PageEntity lentaPage2 = new PageEntity();
        lentaPage2.setSite(lentaSite);
        lentaPage2.setPath("/articles");
        lentaPage2.setCode(200);
        lentaPage2.setContent("<html><body>Articles content for lenta.ru</body></html>");

        PageEntity playbackPage1 = new PageEntity();
        playbackPage1.setSite(playbackSite);
        playbackPage1.setPath("/movies");
        playbackPage1.setCode(200);
        playbackPage1.setContent("<html><body>Movies content for playback.ru</body></html>");

        PageEntity playbackPage2 = new PageEntity();
        playbackPage2.setSite(playbackSite);
        playbackPage2.setPath("/music");
        playbackPage2.setCode(200);
        playbackPage2.setContent("<html><body>Music content for playback.ru</body></html>");

        // Сохраняем страницы
        pageRepository.saveAll(Arrays.asList(lentaPage1, lentaPage2, playbackPage1, playbackPage2));

        System.out.println("Фиктивные данные добавлены.");
    }

}

