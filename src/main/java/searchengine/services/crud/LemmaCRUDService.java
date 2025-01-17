package searchengine.services.crud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class LemmaCRUDService {

    private final LemmaRepository lemmaRepository;

    @Transactional
    public LemmaEntity updateLemmaEntity(String lemmaText, SiteEntity site) {
        if (site == null) {
            return null;
        }

        LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                .orElseGet(() -> createLemmaEntity(lemmaText, site));

        int updatedFrequency = lemma.getFrequency() + 1;
        lemma.setFrequency(updatedFrequency);

        return lemmaRepository.save(lemma);
    }

    void updateOrDeleteLemma(LemmaEntity lemma) {
        int newFrequency = lemma.getFrequency() - 1;
        if (newFrequency > 0) {
            lemma.setFrequency(newFrequency);
            lemmaRepository.save(lemma);
        } else {
            lemmaRepository.delete(lemma);
        }
    }

    private LemmaEntity createLemmaEntity(String lemmaText, SiteEntity site) {
        LemmaEntity newLemma = new LemmaEntity();
        populateLemmaEntity(newLemma, lemmaText, site);
        return newLemma;
    }

    private void populateLemmaEntity(LemmaEntity lemma, String lemmaText, SiteEntity site) {
        lemma.setLemma(lemmaText);
        lemma.setFrequency(0);
        lemma.setSite(site);
    }
}
