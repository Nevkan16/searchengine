//package searchengine.task;
//
//import lombok.AllArgsConstructor;
//import lombok.Getter;
//import lombok.Setter;
//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
//import org.jsoup.select.Elements;
//import searchengine.model.SiteEntity;
//import searchengine.services.PageService;
//
//import java.io.IOException;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.RecursiveTask;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//@Getter
//@Setter
//@AllArgsConstructor
//public class LinkTask extends RecursiveTask<Void> {
//
//    private static final Set<String> visitedLinks = Collections.newSetFromMap(new ConcurrentHashMap<>());
//    private static final AtomicBoolean stopRequest = new AtomicBoolean(false);
//    private Document doc;
//    private String baseUrl;
//    private int depth;
//    private int maxDepth;
//    private PageService pageService;
//    private SiteEntity site;
//
//    @Override
//    protected Void compute() {
//        if (depth > maxDepth || stopRequest.get()) {
//            return null;
//        }
//
//        // Получаем все ссылки на странице
//        Elements links = doc.select("a[href]");
//        Set<LinkTask> subTasks = new HashSet<>();
//
//        // Обрабатываем все ссылки на странице
//        processLinks(links, subTasks);
//
//        // Если есть подзадачи, выполняем их
//        if (stopRequest.get()) {
//            return null;
//        }
//
//        // Запускаем все подзадачи
//        executeSubTasks(subTasks);
//
//        return null;
//    }
//
//    // Метод для обработки ссылок на странице
//    private void processLinks(Elements links, Set<LinkTask> subTasks) {
//        for (Element link : links) {
//            if (stopRequest.get()) {
//                return;
//            }
//
//            String linkHref = link.attr("abs:href");
//
//            if (LinkValidator.isValid(linkHref, baseUrl) && visitedLinks.add(linkHref)) {
//                pageService.savePage(linkHref, baseUrl, site);
//
//                try {
//                    Document childDoc = Jsoup.connect(linkHref).get();
//                    LinkTask subTask = createSubTask(childDoc);
//                    subTasks.add(subTask);
//                } catch (IOException e) {
//                    System.err.println("Error loading link: " + linkHref);
//                }
//            }
//        }
//    }
//
//    // Метод для создания подзадачи для обхода ссылки
//    private LinkTask createSubTask(Document childDoc) {
//        return new LinkTask(childDoc, baseUrl, depth + 1, maxDepth, pageService, site);
//    }
//
//    // Метод для запуска всех подзадач
//    private void executeSubTasks(Set<LinkTask> subTasks) {
//        for (LinkTask subTask : subTasks) {
//            subTask.fork();  // Запуск подзадачи в отдельном потоке
//        }
//
//        // Ожидание завершения всех подзадач
//        for (LinkTask subTask : subTasks) {
//            subTask.join();  // Ждем завершения каждой подзадачи
//        }
//    }
//
//    // Метод для запроса остановки
//    public static void requestStop() {
//        stopRequest.set(true);
//    }
//
//    // Проверка запроса на остановку
//    public static boolean getStopRequest() {
//        return stopRequest.get();
//    }
//
//    // Сброс флага остановки
//    public static void resetStopFlag() {
//        stopRequest.set(false);
//    }
//}
