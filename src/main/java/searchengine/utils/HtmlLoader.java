package searchengine.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;

import java.io.IOException;
@Slf4j
@Service
@RequiredArgsConstructor
public class HtmlLoader {
    private final FakeConfig fakeConfig;

    public Document fetchHtmlDocument(String url) {
        try {
            Thread.sleep(1000); // Задержка для предотвращения блокировки
            return Jsoup.connect(url)
                    .userAgent(fakeConfig.getUserAgent())
                    .referrer(fakeConfig.getReferrer())
                    .get();
        } catch (IOException e) {
            log.error("Failed to load URL: {}. Reason: {}", url, e.getMessage());
        } catch (InterruptedException e) {
            log.error("Thread was interrupted during fetch: {}", e.getMessage());
            Thread.currentThread().interrupt(); // Сбрасываем статус прерывания
        } catch (Exception e) {
            log.error("Unexpected error while fetching URL: {}. Reason: {}", url, e.getMessage());
        }
        return null; // Возвращаем null в случае ошибки
    }

    public void showHtml(String url) throws IOException {
        System.out.println(fetchHtmlDocument(url));
    }
}
