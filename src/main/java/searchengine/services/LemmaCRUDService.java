package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LemmaCRUDService {

    private final LemmaRepository lemmaRepository;

    @Transactional
    public LemmaEntity updateLemmaEntity(String lemmaText, SiteEntity site) {
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

    public Optional<LemmaEntity> findLemma(String lemmaText, SiteEntity site) {
        return lemmaRepository.findByLemmaAndSite(lemmaText, site);
    }

    @Transactional
    public void deleteLemma(LemmaEntity lemma) {
        lemmaRepository.delete(lemma);
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
