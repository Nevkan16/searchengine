package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repository.LemmaRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LemmaCRUDService {

    private final LemmaRepository lemmaRepository;

    @Transactional
    public LemmaEntity updateLemmaEntity(String lemmaText, SiteEntity site, PageEntity page) {
        LemmaEntity lemma = lemmaRepository.findByLemmaAndSite(lemmaText, site)
                .orElseGet(() -> createLemmaEntity(lemmaText, site));

        // Проверяем, на какой странице встречается лемма
        if (!lemma.getPages().contains(page)) {
            lemma.getPages().add(page);  // Добавляем страницу
        }

        // Обновляем частоту как количество уникальных страниц
        lemma.setFrequency(lemma.getPages().size());  // Частота - количество страниц

        return lemmaRepository.save(lemma);  // Сохраняем обновленную лемму
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