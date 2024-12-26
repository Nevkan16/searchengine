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
            log.warn("Не удалось загрузить URL: {}. Причина: {}", url, e.getMessage());
        } catch (InterruptedException e) {
            log.error("Операция загрузки была прервана: {}", e.getMessage());
            Thread.currentThread().interrupt(); // Сбрасываем статус прерывания
        } catch (Exception e) {
            log.error("Неожиданная ошибка при загрузке URL: {}. Причина: {}", url, e.getMessage());
        }
        return null; // Возвращаем null в случае ошибки
    }

    public void showHtml(String url, FakeConfig fakeConfig) {
        Document doc = fetchHtmlDocument(url, fakeConfig);
        if (doc != null) {
            System.out.println(doc);
        } else {
            log.error("HTML-документ не удалось загрузить для URL: {}", url);
        }
    }

    public int getHttpStatusCode(String url) {
        try {
            URI uri = new URI(url);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // Таймаут на подключение
            connection.setReadTimeout(5000);    // Таймаут на чтение

            // Отправляем запрос и получаем статус-код
            int statusCode = connection.getResponseCode();
            log.info("HTTP статус-код для URL {}: {}", url, statusCode);
            connection.disconnect();
            return statusCode;
        } catch (IOException e) {
            log.warn("Не удалось получить HTTP статус-код для URL: {}. Причина: {}", url, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при получении HTTP статус-кода для URL: {}. Причина: {}", url, e.getMessage());
        }
        return -1; // Возвращаем -1, если статус-код получить не удалось
    }
}
