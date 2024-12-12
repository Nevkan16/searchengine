package searchengine.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class FakeConfig {

    @Value("${fake.user-agent}")
    private String userAgent;

    @Value("${fake.referrer}")
    private String referrer;
}

