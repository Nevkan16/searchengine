package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Slf4j
@Service
public class HtmlLoader {
    private static final int TASK_TIMEOUT_SECONDS = 10;
    private boolean useFakeConfigForHtml = true;
    private boolean htmlMethodDetermined = false;
    private boolean useFakeConfigForHttpCode = true;
    private boolean httpCodeMethodDetermined = false;

    public Document fetchHtmlDocument(String url, FakeConfig fakeConfig) {
        return determineMethod(
                url,
                fakeConfig,
                htmlMethodDetermined,
                useFakeConfigForHtml,
                this::fetchWithFakeConfig,
                this::fetchWithoutFakeConfig,
                (result) -> {
                    this.useFakeConfigForHtml = result;
                    this.htmlMethodDetermined = true;
                }
        );
    }

    public int getHttpStatusCode(String url, FakeConfig fakeConfig) {
        return determineMethod(
                url,
                fakeConfig,
                httpCodeMethodDetermined,
                useFakeConfigForHttpCode,
                this::getStatusCodeWithFakeConfig,
                this::getStatusCodeWithoutFakeConfig,
                (result) -> {
                    this.useFakeConfigForHttpCode = result;
                    this.httpCodeMethodDetermined = true;
                }
        );
    }

    private <T> T determineMethod(
            String url,
            FakeConfig fakeConfig,
            boolean methodDetermined,
            boolean useFakeConfig,
            BiFunction<String, FakeConfig, T> withFakeConfig,
            BiFunction<String, FakeConfig, T> withoutFakeConfig,
            Consumer<Boolean> methodSetter
    ) {
        if (methodDetermined) {
            return useFakeConfig ? withFakeConfig.apply(url, fakeConfig) : withoutFakeConfig.apply(url, fakeConfig);
        }

        T result = withFakeConfig.apply(url, fakeConfig);
        if (result != null) {
            methodSetter.accept(true);
            return result;
        }

        result = withoutFakeConfig.apply(url, fakeConfig);
        if (result != null) {
            methodSetter.accept(false);
        }
        return result;
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

    private Document fetchWithoutFakeConfig(String url, FakeConfig fakeConfig) {
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
            log.info("HTTP-код {} через {}: {}", url, useFakeConfig ? "FakeConfig" : "без FakeConfig", statusCode);
            connection.disconnect();
            return statusCode;
        } catch (IOException e) {
            log.warn("Ошибка HTTP-кода {} через {}: Причина: {}",
                    url, useFakeConfig ? "FakeConfig" : "без FakeConfig", e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка HTTP-кода {} через {}: Причина: {}",
                    url, useFakeConfig ? "FakeConfig" : "без FakeConfig", e.getMessage());
        }
        return -1;
    }
}
