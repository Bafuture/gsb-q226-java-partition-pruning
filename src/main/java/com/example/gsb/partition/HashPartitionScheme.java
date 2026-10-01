package com.example.gsb.partition;

/**
 * 按哈希分区：分区键经哈希取模落入固定数量的桶，每个桶是一个分区。
 *
 * <p>哈希打散后，单个桶内的键在原始键空间上是离散的，无法用连续区间表达，
 * 因此 {@link #boundsOf(String)} 返回全域范围：范围查询无法裁剪任何哈希分区，
 * 只有等值查询能通过 {@link #candidatesForEqual(long)} 精确命中一个桶。
 */
public final class HashPartitionScheme implements PartitionScheme {

    private final int buckets;

    public HashPartitionScheme(int buckets) {
        if (buckets <= 0) {
            throw new IllegalArgumentException("buckets must be positive, got " + buckets);
        }
        this.buckets = buckets;
    }

    public int buckets() {
        return buckets;
    }

    @Override
    public String partitionOf(long partitionKey) {
        return "bucket-" + Math.floorMod(Long.hashCode(partitionKey), buckets);
    }

    @Override
    public KeyRange boundsOf(String partitionId) {
        return KeyRange.all();
    }

    @Override
    public String toString() {
        return "HashPartitionScheme[buckets=" + buckets + "]";
    }
}
