package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteDataExecutor siteDataExecutor;
    private final SiteService siteService;

    @Override
    public boolean startIndexing() {
        // Проверяем, выполняется ли уже индексация
        if (siteService.isIndexing()) {
            System.out.println("Индексация уже запущена.");
            return false;
        }

        try {
            System.out.println("Запуск обновления данных сайтов...");
            siteDataExecutor.refreshAllSitesData();

            System.out.println("Запуск процесса индексации сайтов...");
            siteService.processSites();

            return true;
        } catch (Exception e) {
            System.err.println("Ошибка при запуске индексации: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean stopIndexing() {
        System.out.println("Остановка процесса индексации...");
        siteService.stopProcessing();
        return true;
    }

    @Override
    public boolean indexPage(String url) {
        System.out.println("Метод indexPage ещё не реализован.");
        return false;
    }
}
