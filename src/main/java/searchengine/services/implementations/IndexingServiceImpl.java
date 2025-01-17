package searchengine.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.constants.ErrorMessages;
import searchengine.repository.PageRepository;
import searchengine.utils.PageProcessorUtil;
import searchengine.services.SiteDataExecutor;
import searchengine.services.SiteIndexingService;
import searchengine.services.interfaces.IndexingService;
import searchengine.task.LinkProcessorTask;
import searchengine.utils.ConfigUtil;
import searchengine.utils.HtmlLoaderUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataExecutor siteDataExecutor;
    private final SiteIndexingService siteIndexingService;
    private final PageProcessorUtil pageProcessorUtil;
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
            LinkProcessorTask.clearVisitedLinks();
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
        if (HtmlLoaderUtil.getPath(url).isEmpty() || HtmlLoaderUtil.getPath(url) == null) {
            log.info("Индексация страницы остановлена: путь страницы не передан.");
            return false;
        }

        try {
            pageProcessorUtil.processPage(url);
            return pageRepository.findByPath(HtmlLoaderUtil.getPath(url)).isPresent();
        } catch (Exception e) {
            log.info("Ошибка при индексации страницы {}: {}", url, e.getMessage());
            return false;
        }
    }
}