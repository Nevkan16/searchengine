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
        // Используем метод siteService для проверки текущего статуса индексации
        if (siteService.isIndexing()) {
            System.out.println("Индексация уже запущена.");
            return false;
        }

        try {
            System.out.println("Запуск процесса индексации...");
            siteDataExecutor.refreshAllSitesData();
            siteService.processSites(); // Запускаем процесс индексации
            return true;
        } catch (Exception e) {
            System.err.println("Ошибка при запуске индексации: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean stopIndexing() {
        // Проверяем, выполняется ли индексация через siteService
        if (!siteService.isIndexing()) {
            System.out.println("Индексация не запущена.");
            return false;
        }

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
