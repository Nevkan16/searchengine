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
    private static final int TASK_TIMEOUT_SECONDS = 10;
    private boolean useFakeConfigForHtml = true;
    private boolean htmlMethodDetermined = false;
    private boolean useFakeConfigForHttpCode = true;
    private boolean httpCodeMethodDetermined = false;

    public Document fetchHtmlDocument(String url, FakeConfig fakeConfig) {
        if (htmlMethodDetermined) {
            return useFakeConfigForHtml ? fetchWithFakeConfig(url, fakeConfig) : fetchWithoutFakeConfig(url);
        }

        Document document = fetchWithFakeConfig(url, fakeConfig);
        if (document != null) {
            useFakeConfigForHtml = true;
            htmlMethodDetermined = true;
            return document;
        }

        document = fetchWithoutFakeConfig(url);
        if (document != null) {
            useFakeConfigForHtml = false;
            htmlMethodDetermined = true;
        }
        return document;
    }

    private Document fetchWithFakeConfig(String url, FakeConfig fakeConfig) {
        try {
            log.info("Загрузка URL через FakeConfig");
            Thread.sleep(1000); // Задержка для предотвращения блокировки
            return Jsoup.connect(url)
                    .userAgent(fakeConfig.getUserAgent())
                    .referrer(fakeConfig.getReferrer())
                    .timeout(TASK_TIMEOUT_SECONDS * 1000)
                    .get();
        } catch (IOException e) {
            log.warn("Ошибка загрузки URL через FakeConfig: {}. Причина: {}", url, e.getMessage());
        } catch (InterruptedException e) {
            log.error("Операция прервана: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private Document fetchWithoutFakeConfig(String url) {
        try {
            log.info("Загрузка URL без FakeConfig");
            return Jsoup.connect(url)
                    .timeout(TASK_TIMEOUT_SECONDS * 1000)
                    .get();
        } catch (IOException e) {
            log.warn("Ошибка загрузки URL без FakeConfig: {}. Причина: {}", url, e.getMessage());
        }
        return null;
    }

    public void showHtml(String url, FakeConfig fakeConfig) {
        Document doc = fetchHtmlDocument(url, fakeConfig);
        if (doc != null) {
            System.out.println(doc);
        } else {
            log.error("HTML-документ не удалось загрузить для URL: {}", url);
        }
    }

    public int getHttpStatusCode(String url, FakeConfig fakeConfig) {
        if (httpCodeMethodDetermined) {
            return useFakeConfigForHttpCode ? getStatusCodeWithFakeConfig(url, fakeConfig) : getStatusCodeWithoutFakeConfig(url);
        }

        int statusCode = getStatusCodeWithFakeConfig(url, fakeConfig);
        if (statusCode != -1) {
            useFakeConfigForHttpCode = true;
            httpCodeMethodDetermined = true;
            return statusCode;
        }

        statusCode = getStatusCodeWithoutFakeConfig(url);
        if (statusCode != -1) {
            useFakeConfigForHttpCode = false;
            httpCodeMethodDetermined = true;
        }
        return statusCode;
    }

    private int getStatusCodeWithFakeConfig(String url, FakeConfig fakeConfig) {
        try {
            URI uri = new URI(url);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", fakeConfig.getUserAgent());
            connection.setRequestProperty("Referer", fakeConfig.getReferrer());
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int statusCode = connection.getResponseCode();
            log.info("HTTP-код через FakeConfig для {}: {}", url, statusCode);
            connection.disconnect();
            return statusCode;
        } catch (IOException e) {
            log.warn("Ошибка получения HTTP-кода через FakeConfig: {}. Причина: {}", url, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при HTTP-коде через FakeConfig: {}. Причина: {}", url, e.getMessage());
        }
        return -1;
    }

    private int getStatusCodeWithoutFakeConfig(String url) {
        try {
            log.info("Получаем HTTP-код без FakeConfig: {}", url);
            URI uri = new URI(url);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int statusCode = connection.getResponseCode();
            log.info("HTTP-код без FakeConfig для {}: {}", url, statusCode);
            connection.disconnect();
            return statusCode;
        } catch (IOException e) {
            log.warn("Ошибка HTTP-кода без FakeConfig: {}. Причина: {}", url, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при HTTP-коде без FakeConfig: {}. Причина: {}", url, e.getMessage());
        }
        return -1;
    }
}
