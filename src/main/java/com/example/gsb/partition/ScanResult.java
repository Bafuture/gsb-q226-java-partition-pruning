package com.example.gsb.partition;

import java.util.List;
import java.util.Objects;

/**
 * 查询执行结果：去重并排序后的行，以及本次查询的分区裁剪报告。
 */
public record ScanResult(List<Row> rows, PruningReport pruning) {

    public ScanResult {
        Objects.requireNonNull(rows, "rows must not be null");
        Objects.requireNonNull(pruning, "pruning must not be null");
        rows = List.copyOf(rows);
    }
}
