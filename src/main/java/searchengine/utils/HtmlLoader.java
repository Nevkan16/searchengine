package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

@Slf4j
@Service
public class HtmlLoader {
    private static final int TASK_TIMEOUT_SECONDS = 10;
    private final ConcurrentHashMap<String, Boolean> domainMethodMap = new ConcurrentHashMap<>();

    public Document fetchHtmlDocument(String url, FakeConfig fakeConfig) {
        return determineMethod(
                url,
                fakeConfig,
                this::fetchWithFakeConfig,
                this::fetchWithoutFakeConfig
        );
    }

    public int getHttpStatusCode(String url, FakeConfig fakeConfig) {
        return determineMethod(
                url,
                fakeConfig,
                this::getStatusCodeWithFakeConfig,
                this::getStatusCodeWithoutFakeConfig
        );
    }

    private <T> T determineMethod(
            String url,
            FakeConfig fakeConfig,
            BiFunction<String, FakeConfig, T> withFakeConfig,
            BiFunction<String, FakeConfig, T> withoutFakeConfig
    ) {
        String baseUrl = getBaseUrl(url);

        Boolean useFakeConfig = domainMethodMap.get(baseUrl);

        if (useFakeConfig != null) {
            return useFakeConfig ? withFakeConfig.apply(url, fakeConfig) : withoutFakeConfig.apply(url, fakeConfig);
        }

        T result = withFakeConfig.apply(url, fakeConfig);
        if (isSuccessful(result)) {
            domainMethodMap.put(baseUrl, true);
            return result;
        }

        result = withoutFakeConfig.apply(url, fakeConfig);
        if (isSuccessful(result)) {
            domainMethodMap.put(baseUrl, false); // Запоминаем решение
        }
        return result;
    }

    private boolean isSuccessful(Object result) {
        if (result instanceof Document) {
            return true;
        } else if (result instanceof Integer) {
            int code = (Integer) result;
            return code >= 200 && code < 300;
        }
        return false;
    }

    private Document fetchWithFakeConfig(String url, FakeConfig fakeConfig) {
        try {
            log.info("Загрузка URL через FakeConfig: {}", url);
            Thread.sleep(1000);
            return Jsoup.connect(url)
                    .userAgent(fakeConfig.getUserAgent())
                    .referrer(fakeConfig.getReferrer())
                    .timeout(TASK_TIMEOUT_SECONDS * 1000)
                    .get();
        } catch (IOException e) {
            log.warn("Ошибка загрузки URL через FakeConfig: {}", url);
        } catch (InterruptedException e) {
            log.error("Операция прервана: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
        return null;
    }

    private Document fetchWithoutFakeConfig(String url, FakeConfig fakeConfig) {
        try {
            log.info("Загрузка URL без FakeConfig: {}", url);
            return Jsoup.connect(url)
                    .timeout(TASK_TIMEOUT_SECONDS * 1000)
                    .get();
        } catch (IOException e) {
            log.warn("Ошибка загрузки URL без FakeConfig: {}.", url);
        }
        return null;
    }

    private int getStatusCodeWithFakeConfig(String url, FakeConfig fakeConfig) {
        return fetchHttpStatusCode(url, fakeConfig, true);
    }

    private int getStatusCodeWithoutFakeConfig(String url, FakeConfig fakeConfig) {
        return fetchHttpStatusCode(url, fakeConfig, false);
    }

    private int fetchHttpStatusCode(String url, FakeConfig fakeConfig, boolean useFakeConfig) {
        try {
            URI uri = new URI(url);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (useFakeConfig && fakeConfig != null) {
                connection.setRequestProperty("User-Agent", fakeConfig.getUserAgent());
                connection.setRequestProperty("Referer", fakeConfig.getReferrer());
            }

            int statusCode = connection.getResponseCode();
            log.info("HTTP-код {} {}: {}", url, useFakeConfig ? "FakeConfig" : "без FakeConfig", statusCode);
            connection.disconnect();
            return statusCode;
        } catch (IOException e) {
            log.warn("Ошибка HTTP-кода {} {}: Причина: {}",
                    url, useFakeConfig ? "FakeConfig" : "без FakeConfig", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка HTTP-кода {} {}: Причина: {}",
                    url, useFakeConfig ? "FakeConfig" : "без FakeConfig", e.getMessage());
        }
        return -1;
    }

    public static String getSchemeBaseUrl(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(), uri.getHost(), null, null).toString();
        } catch (Exception e) {
            log.error("Ошибка извлечения схемы URL: {}: {}", url, e.getMessage());
            return url;
        }
    }

    public static String getBaseUrl(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (Exception e) {
            log.error("Ошибка извлечения базового URL из {}: {}", url, e.getMessage());
            return url;
        }
    }

    public static String getPath(String path) {
        try {
            return new URI(path).getPath();
        } catch (Exception e) {
            log.error("Ошибка извлечения пути из URL: {} {}", path, e.getMessage());
            return "";
        }
    }
}