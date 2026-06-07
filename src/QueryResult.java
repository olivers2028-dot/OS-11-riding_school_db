//GENERATED USING AI

import java.util.List;
import java.util.Map;

public class QueryResult {
    private final List<String> columns;
    private final List<List<Object>> rows;
    private final List<Integer> rowIds;
    private final List<Map<String, Object>> rowMaps;

    public QueryResult(List<String> columns, List<List<Object>> rows, List<Integer> rowIds, List<Map<String, Object>> rowMaps) {
        this.columns = columns;
        this.rows = rows;
        this.rowIds = rowIds;
        this.rowMaps = rowMaps;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }

    public List<Integer> getRowIds() {
        return rowIds;
    }

    public List<Map<String, Object>> getRowMaps() {
        return rowMaps;
    }
}
