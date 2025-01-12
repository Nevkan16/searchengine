package searchengine.dto.search;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Pagination {
    private int totalResults;
    private int totalPages;
    private int currentPage;
    private int limit;
    private int offset;
}

