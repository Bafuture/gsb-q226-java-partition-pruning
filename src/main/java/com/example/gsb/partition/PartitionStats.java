package com.example.gsb.partition;

import java.util.Objects;

/**
 * 分区元数据统计：分区 id、覆盖的分区键范围、行数、索引大小（估计字节数）。
 */
public record PartitionStats(String partitionId, KeyRange keyRange, int rowCount, long indexSizeBytes) {

    public PartitionStats {
        Objects.requireNonNull(partitionId, "partitionId must not be null");
        Objects.requireNonNull(keyRange, "keyRange must not be null");
    }
}
