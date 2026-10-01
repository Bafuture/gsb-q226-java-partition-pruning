package com.example.gsb.partition;

import java.util.Comparator;
import java.util.Objects;

/**
 * 一行数据。
 *
 * @param rowId        全局唯一行标识，用于跨分区合并时去重
 * @param partitionKey 分区键（例如按天的日期值，或用于哈希的整数键）
 * @param sortKey      分区内索引键（例如事件时间戳），用于点查/范围查与结果排序
 * @param payload      业务数据
 */
public record Row(long rowId, long partitionKey, long sortKey, String payload) {

    public Row {
        Objects.requireNonNull(payload, "payload");
    }

    /** 合并结果的默认顺序：先按 sortKey，再按 rowId，保证全序且稳定。 */
    static final Comparator<Row> ORDER =
            Comparator.comparingLong(Row::sortKey).thenComparingLong(Row::rowId);
}
