package searchengine.constants;

public interface ErrorMessages {
    String INDEXING_ALREADY_RUNNING = "Индексация уже запущена";
    String INDEXING_NOT_RUNNING = "Индексация не запущена";
    String SITE_DATA_DELETION_ERROR = "Ошибка при удалении фиктивных данных";
    String SERVICE_EXECUTION_ERROR = "Метод не выполнен";
    String PAGE_OUTSIDE_CONFIGURED_SITES =
            "Данная страница находится за пределами сайтов, указанных в конфигурационном файле";
    String SITE_UNAVAILABLE = "Сайт не доступен";
    String PAGE_UNAVAILABLE = "Страница не доступна";
    String INTERRUPTED_OPERATION = "Операция прервана при загрузке";
    String UNKNOWN_ERROR = "Неизвестная ошибка";
    String NOT_GET_HTTP_CODE = "Отсутствует HTTP ответ";
    String UNKNOWN_HTTP_ERROR = "Неизвестная HTTP ошибка";
    String ERROR_LOAD_CHILD_PAGE = "Ссылка не доступна";
    String ERROR_SAVE_PAGE_TO_DATABASE = "Ошибка сохранения страницы";
    String PAGES_NOT_FOUND = "Страницы отсутствуют";
    String EMPTY_QUERY = "Задан пустой поисковый запрос";
    String INDEXING_ERROR = "Ошибка в процессе индексации";
    String ERROR_START_INDEXING = "Ошибка при запуске индексации";
    String FAILED_TO_LOAD_HTML = "Не удалось загрузить HTML-документ для сайта: ";
    String ERROR_PROCESS_SITE = "Ошибка при обработке сайта: ";
    String PROCESS_NOT_RUNNING = "Процесс не запущен!";

}

