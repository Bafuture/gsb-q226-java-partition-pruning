package com.example.gsb.partition;

import java.util.List;
import java.util.Objects;

/**
 * 一个分区：持有自己的 {@link PartitionIndex}，并声明自身覆盖的分区键范围。
 */
public final class Partition {

    private final String id;
    private final KeyRange bounds;
    private final PartitionIndex index = new PartitionIndex();

    Partition(String id, KeyRange bounds) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.bounds = Objects.requireNonNull(bounds, "bounds must not be null");
    }

    public String id() {
        return id;
    }

    public KeyRange bounds() {
        return bounds;
    }

    public void put(Row row) {
        index.put(Objects.requireNonNull(row, "row must not be null"));
    }

    public Row get(String id) {
        return index.get(id);
    }

    /** 主键区间扫描，端点为 null 表示不限制。 */
    public List<Row> scan(String fromIdInclusive, String toIdExclusive) {
        return index.scan(fromIdInclusive, toIdExclusive);
    }

    public int rowCount() {
        return index.rowCount();
    }

    public long indexSizeBytes() {
        return index.sizeBytes();
    }

    /** 清空索引数据，分区对象本身随后会从表中摘除。 */
    void drop() {
        index.clear();
    }

    public PartitionStats stats() {
        return new PartitionStats(id, bounds, index.rowCount(), index.sizeBytes());
    }
}
