package searchengine.constants;

public class ErrorMessages {
    public static final String INDEXING_ALREADY_RUNNING = "Индексация уже запущена";
    public static final String INDEXING_NOT_RUNNING = "Индексация не запущена";
    public static final String SITE_DATA_DELETION_ERROR = "Ошибка при удалении фиктивных данных";
    public static final String SERVICE_EXECUTION_ERROR = "Метод не выполнен";
    public static final String PAGE_OUTSIDE_CONFIGURED_SITES =
            "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";

    public static final String SITE_UNAVAILABLE = "Сайт не доступен";

    public static final String INTERRUPTED_OPERATION = "Операция прервана при загрузке";

    public static final String UNKNOWN_ERROR = "Неизвестная ошибка";

    public static final String NOT_GET_HTTP_CODE = "Отсутствует HTTP ответ";

    public static final String UNKNOWN_HTTP_ERROR = "Неизвестная HTTP ошибка";

    public static final String ERROR_LOAD_CHILD_PAGE = "Ссылка не доступна";

    public static final String ERROR_SAVE_PAGE_TO_DATABASE = "Ошибка сохранения страницы";
    public static final String PAGES_NOT_FOUND = "Страницы отсутствуют";


    private ErrorMessages() {
        // Приватный конструктор, чтобы предотвратить создание экземпляра
    }
}

