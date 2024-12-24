package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.repository.PageRepository;

import javax.transaction.Transactional;
import java.net.URI;
@Slf4j
@Service
@RequiredArgsConstructor
public class TestMethod {
    private final PageRepository pageRepository;
    @Transactional
    public void deletePageByUrl(String url) {
        try {
            // Извлекаем путь из URL
            URI uri = new URI(url);
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                log.error("Некорректный URL: {}", url);
                return;
            }

            // Вызываем метод удаления по пути
            pageRepository.findByPath(path).ifPresent(page -> {
                pageRepository.delete(page);
                log.info("Страница с URL {} успешно удалена.", url);
            });

        } catch (Exception e) {
            log.error("Ошибка при удалении страницы с URL: {}", url, e);
        }
    }

    public void testDeletePage() {
        deletePageByUrl("https://www.playback.ru/basket.html");
    }

}
