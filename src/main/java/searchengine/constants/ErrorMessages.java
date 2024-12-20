package searchengine.constants;

public class ErrorMessages {
    public static final String INDEXING_ALREADY_RUNNING = "Индексация уже запущена";
    public static final String INDEXING_NOT_RUNNING = "Индексация не запущена";
    public static final String SITE_DATA_DELETION_ERROR = "Ошибка при удалении фиктивных данных";
    public static final String SERVICE_EXECUTION_ERROR = "Метод не выполнен";
    public static final String PAGE_OUTSIDE_CONFIGURED_SITES =
            "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";


    private ErrorMessages() {
        // Приватный конструктор, чтобы предотвратить создание экземпляра
    }
}

