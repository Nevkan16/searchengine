package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.constants.ErrorMessages;
import searchengine.repository.PageRepository;
import searchengine.task.LinkProcessor;
import searchengine.utils.ConfigUtil;
import searchengine.utils.HtmlLoader;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataExecutor siteDataExecutor;
    private final SiteIndexingService siteIndexingService;
    private final PageProcessor pageProcessor;
    private final ConfigUtil configUtil;
    private final PageRepository pageRepository;

    @Override
    public boolean startIndexing() {
        if (siteIndexingService.isIndexing()) {
            log.info(ErrorMessages.INDEXING_ALREADY_RUNNING);
            return false;
        }

        try {
            log.info("Запуск процесса индексации...");
            LinkProcessor.clearVisitedLinks();
            siteDataExecutor.refreshAllSitesData();
            siteIndexingService.processSites();
            return true;
        } catch (Exception e) {
            log.error(ErrorMessages.ERROR_START_INDEXING + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean stopIndexing() {
        if (!siteIndexingService.isIndexing()) {
            log.info(ErrorMessages.INDEXING_NOT_RUNNING);
            return false;
        }

        log.info("Остановка процесса индексации...");
        siteIndexingService.stopProcessing();
        return true;
    }

    @Override
    public boolean indexPage(String url) {
        log.info("Запуск индексации отдельной страницы. Переданный URL от пользователя: {}", url);

        url = configUtil.validateURL(url);

        if (url == null) {
            log.info("Индексация страницы остановлена: некорректный URL.");
            return false;
        }
        if (HtmlLoader.getPath(url).isEmpty() || HtmlLoader.getPath(url) == null) {
            log.info("Индексация страницы остановлена: путь страницы не передан.");
            return false;
        }

        try {
            pageProcessor.processPage(url);
            return pageRepository.findByPath(HtmlLoader.getPath(url)).isPresent();
        } catch (Exception e) {
            log.info("Ошибка при индексации страницы {}: {}", url, e.getMessage());
            return false;
        }
    }
}