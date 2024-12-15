package searchengine.exception;

public enum HttpResponseStatus {
    // Ошибки
    NOT_FOUND(404, "Page not found"),
    UNAUTHORIZED(401, "Unauthorized access"),
    FORBIDDEN(403, "Access forbidden"),
    INTERNAL_SERVER_ERROR(500, "Internal server error"),
    BAD_GATEWAY(502, "Bad gateway"),
    SERVICE_UNAVAILABLE(503, "Service unavailable"),
    CONNECTION_REFUSED(0, "Connection refused"),
    TIMEOUT(0, "Request timeout"),
    DNS_RESOLUTION_FAILURE(0, "DNS resolution failure"),

    // Успешные ответы
    OK(200, "Request succeeded"),
    CREATED(201, "Resource created"),
    ACCEPTED(202, "Request accepted but not processed yet"),
    NO_CONTENT(204, "No content to return"),
    MOVED_PERMANENTLY(301, "Resource moved permanently"),
    FOUND(302, "Resource found temporarily");

    private final int code;
    private final String message;

    HttpResponseStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static HttpResponseStatus fromCode(int code) {
        for (HttpResponseStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}

