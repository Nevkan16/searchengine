package searchengine.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

@Service
public class DataService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    public DataService(SiteRepository siteRepository, PageRepository pageRepository) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional
    public boolean deleteSiteData(String siteUrl) {
        SiteEntity site = siteRepository.findByUrl(siteUrl);

        if (site == null) {
            return false;
        }

        pageRepository.deleteBySite(site);
        siteRepository.delete(site);

        return true;
    }
}

