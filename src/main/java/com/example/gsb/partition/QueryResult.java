package com.example.gsb.partition;

import java.util.List;

/**
 * 查询结果。
 *
 * @param rows                    合并、去重、排序后的结果（按 sortKey, rowId）
 * @param partitionsBeforePruning 裁剪前表中分区总数
 * @param partitionsAfterPruning  裁剪后实际扫描的分区数
 * @param scannedPartitions       实际扫描的分区标识（有序）
 */
public record QueryResult(
        List<Row> rows,
        int partitionsBeforePruning,
        int partitionsAfterPruning,
        List<String> scannedPartitions) {

    /** 被裁剪掉的分区数。 */
    public int prunedPartitions() {
        return partitionsBeforePruning - partitionsAfterPruning;
    }
}
