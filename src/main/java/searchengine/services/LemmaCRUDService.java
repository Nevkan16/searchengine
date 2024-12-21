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
        // Найти лемму по тексту и сайту, если не найдена, создать новую
        LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                .orElseGet(() -> createLemmaEntity(lemmaText, site));

        // Обновить частоту
        updateLemmaFrequency(lemma);

        return lemmaRepository.save(lemma); // Сохранить и вернуть
    }

    public Optional<LemmaEntity> findLemma(String lemmaText, SiteEntity site) {
        return lemmaRepository.findByLemmaAndSite(lemmaText, site);
    }

    @Transactional
    public void deleteLemma(LemmaEntity lemma) {
        lemmaRepository.delete(lemma);
    }


    // Метод для создания новой леммы
    private LemmaEntity createLemmaEntity(String lemmaText, SiteEntity site) {
        LemmaEntity newLemma = new LemmaEntity();
        populateLemmaEntity(newLemma, lemmaText, site);
        return newLemma;
    }

    // Метод для обновления частоты леммы
    private void updateLemmaFrequency(LemmaEntity lemma) {
        lemma.setFrequency(lemma.getFrequency() + 1);
    }

    // Метод для заполнения леммы
    private void populateLemmaEntity(LemmaEntity lemma, String lemmaText, SiteEntity site) {
        lemma.setLemma(lemmaText);
        lemma.setFrequency(0);
        lemma.setSite(site);
    }

}
