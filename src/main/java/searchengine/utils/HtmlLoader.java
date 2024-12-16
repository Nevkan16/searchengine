package searchengine.utils;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.FakeConfig;

import java.io.IOException;
@Service
@RequiredArgsConstructor
public class HtmlLoader {
    private final FakeConfig fakeConfig;

    public Document fetchHtmlDocument(String url) throws IOException {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        return Jsoup.connect(url)
                .userAgent(fakeConfig.getUserAgent())
                .referrer(fakeConfig.getReferrer())
                .get();
    }

    public void showHtml(String url) throws IOException {
        System.out.println(fetchHtmlDocument(url));
    }
}
