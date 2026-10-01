package com.example.gsb.partition;

import java.util.Set;

/**
 * 分区策略：决定一个分区键值落入哪个分区，以及每个分区覆盖的键范围。
 *
 * <p>典型实现：按日期范围分区（{@link DateRangePartitionScheme}）与按哈希分区
 * （{@link HashPartitionScheme}）。
 */
public interface PartitionScheme {

    /** 返回分区键值对应的分区 id。 */
    String partitionOf(long partitionKey);

    /**
     * 返回分区覆盖的键范围。该范围用于范围查询时的分区裁剪：
     * 分区范围与查询范围相交才保留为候选分区。
     */
    KeyRange boundsOf(String partitionId);

    /**
     * 等值查询时直接命中的分区集合。日期/范围分区与哈希分区都能精确命中一个分区；
     * 哈希分区的 {@link #boundsOf(String)} 无法表达离散键集合，所以需要该方法做等值裁剪。
     */
    default Set<String> candidatesForEqual(long partitionKey) {
        return Set.of(partitionOf(partitionKey));
    }
}
