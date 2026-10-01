package com.example.gsb.partition;

import java.util.OptionalLong;

/**
 * 单个分区的元数据统计。
 *
 * @param partitionId      分区标识
 * @param partitionKeyMin  分区覆盖的分区键下界
 * @param partitionKeyMax  分区覆盖的分区键上界
 * @param rowCount         分区内数据条数
 * @param indexSizeBytes   分区索引估算大小（字节）
 * @param sortKeyMin       实际数据中的最小索引键（空分区为空）
 * @param sortKeyMax       实际数据中的最大索引键（空分区为空）
 */
public record PartitionStats(
        String partitionId,
        long partitionKeyMin,
        long partitionKeyMax,
        long rowCount,
        long indexSizeBytes,
        OptionalLong sortKeyMin,
        OptionalLong sortKeyMax) {
}
