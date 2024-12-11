package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList siteList;

    public void savePage(String linkHref, String baseUrl, SiteEntity site) {
        try {
            Connection.Response response = Jsoup.connect(linkHref).execute();
            if (response.statusCode() == 200) {
                PageEntity page = new PageEntity();
                page.setSite(site);
                page.setPath(linkHref.replace(baseUrl, ""));
                page.setCode(response.statusCode());
                page.setContent(response.body());
                pageRepository.save(page);
                System.out.println("Saved page: " + linkHref);
            } else {
                System.out.println("Failed to load page: " + linkHref + " Status: " + response.statusCode());
            }
        } catch (IOException e) {
            System.err.println("Error saving page: " + linkHref + ". Error: " + e.getMessage());
        }
    }

    @Transactional
    public void cleanData() {
        siteList.getSites().forEach(site -> {
            String siteUrl = site.getUrl();
            Optional<SiteEntity> siteEntity = siteRepository.findByUrl(siteUrl);
            if (siteEntity.isPresent()) {
                pageRepository.deleteBySite(siteEntity.get());
                siteRepository.delete(siteEntity.get());
            }
        });
    }
}

