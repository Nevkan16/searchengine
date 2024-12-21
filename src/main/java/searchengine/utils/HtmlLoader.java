package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

@Slf4j
@Service
public class HtmlLoader {

    public Document fetchHtmlDocument(String url, FakeConfig fakeConfig) {
        try {
            Thread.sleep(1000); // Задержка для предотвращения блокировки
            return Jsoup.connect(url)
                    .userAgent(fakeConfig.getUserAgent()) // Использование значений из FakeConfig
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

    public void showHtml(String url, FakeConfig fakeConfig) throws IOException {
        System.out.println(fetchHtmlDocument(url, fakeConfig));
    }

    public int getHttpStatusCode(String url) throws Exception {
        URI uri = new URI(url);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000); // Таймаут на подключение
        connection.setReadTimeout(5000);    // Таймаут на чтение

        // Отправляем запрос и получаем статус-код
        int statusCode = connection.getResponseCode();
        connection.disconnect();

        return statusCode;
    }
}
