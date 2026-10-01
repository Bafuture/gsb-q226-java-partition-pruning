package com.example.gsb.partition;

/**
 * 按分区键取值范围（如日期段）划分分区：每个分区覆盖一段连续、等宽的键区间。
 * 例如 width=30 且分区键是 epoch day 时，相当于按月分区。
 *
 * <p>范围查询只与少数几个区间相交，因此裁剪效果好。
 */
public final class RangePartitioner implements Partitioner {

    private final long width;

    public RangePartitioner(long width) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive, got " + width);
        }
        this.width = width;
    }

    @Override
    public String partitionIdFor(long partitionKey) {
        return "range-" + bucketIndex(partitionKey);
    }

    @Override
    public long keyMinOf(long partitionKey) {
        return bucketIndex(partitionKey) * width;
    }

    @Override
    public long keyMaxOf(long partitionKey) {
        return keyMinOf(partitionKey) + width - 1;
    }

    private long bucketIndex(long partitionKey) {
        return Math.floorDiv(partitionKey, width);
    }
}
