package com.example.gsb.partition;

/**
 * 按分区键哈希取模划分固定数量的分区。数据均匀分布，但各分区覆盖整个键域，
 * 因此只有“分区键点查”能裁剪到单个分区；分区键范围查询无法裁剪。
 */
public final class HashPartitioner implements Partitioner {

    private final int bucketCount;

    public HashPartitioner(int bucketCount) {
        if (bucketCount <= 0) {
            throw new IllegalArgumentException("bucketCount must be positive, got " + bucketCount);
        }
        this.bucketCount = bucketCount;
    }

    @Override
    public String partitionIdFor(long partitionKey) {
        return "hash-" + Math.floorMod(partitionKey, bucketCount);
    }

    @Override
    public long keyMinOf(long partitionKey) {
        return Long.MIN_VALUE;
    }

    @Override
    public long keyMaxOf(long partitionKey) {
        return Long.MAX_VALUE;
    }
}
