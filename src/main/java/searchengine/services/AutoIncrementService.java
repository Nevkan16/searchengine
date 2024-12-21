//package searchengine.services;
//
//import javax.persistence.EntityManager;
//import javax.persistence.PersistenceContext;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import searchengine.repository.PageRepository;
//import searchengine.repository.SiteRepository;
//import searchengine.repository.LemmaRepository;
//import searchengine.repository.IndexRepository;
//
//@Service
//public class AutoIncrementService {
//
//    @PersistenceContext
//    private EntityManager entityManager;
//
//    @Autowired
//    private SiteRepository siteRepository;
//
//    @Autowired
//    private PageRepository pageRepository;
//
//    @Autowired
//    private LemmaRepository lemmaRepository;
//
//    @Autowired
//    private IndexRepository indexRepository;
//
//    @Transactional
//    // Метод для сброса автоинкремента для таблиц, если они пустые
//    public void resetAutoIncrementIfTableIsEmpty() {
//        // Проверяем, пустые ли таблицы и сбрасываем инкремент, если пустые
//        if (siteRepository.count() == 0) {
//            resetAutoIncrement("site");
//        }
//        if (pageRepository.count() == 0) {
//            resetAutoIncrement("page");
//        }
//        if (lemmaRepository.count() == 0) {
//            resetAutoIncrement("lemma");
//        }
//        if (indexRepository.count() == 0) {
//            resetAutoIncrement("index");
//        }
//        System.out.println("Site count: " + siteRepository.count());
//        System.out.println("Page count: " + pageRepository.count());
//        System.out.println("Lemma count: " + lemmaRepository.count());
//        System.out.println("Index count: " + indexRepository.count());
//
//    }
//
//    // Метод для сброса автоинкремента
//    private void resetAutoIncrement(String tableName) {
//        try {
//            entityManager.createNativeQuery("ALTER TABLE " + tableName + " AUTO_INCREMENT = 1").executeUpdate();
//            System.out.println("Автоинкремент сброшен для таблицы: " + tableName);
//        } catch (Exception e) {
//            System.out.println("Ошибка при сбросе автоинкремента для таблицы " + tableName + ": " + e.getMessage());
//        }
//    }
//}
