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
    public LemmaEntity createOrUpdateLemma(String lemmaText, SiteEntity site) {
        // Найти лемму по тексту и сайту
        LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                .orElseGet(() -> {
                    // Если лемма не найдена, создать новую
                    LemmaEntity newLemma = new LemmaEntity();
                    newLemma.setLemma(lemmaText);
                    newLemma.setFrequency(0);
                    newLemma.setSite(site);
                    return newLemma;
                });

        // Обновить частоту
        lemma.setFrequency(lemma.getFrequency() + 1);
        return lemmaRepository.save(lemma); // Сохранить и вернуть
    }

    public Optional<LemmaEntity> findLemma(String lemmaText, SiteEntity site) {
        return lemmaRepository.findByLemmaAndSite(lemmaText, site);
    }

    @Transactional
    public void deleteLemma(LemmaEntity lemma) {
        lemmaRepository.delete(lemma);
    }
}

