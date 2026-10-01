package com.example.gsb.partition;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * 单个分区：维护自己的有序索引（sortKey -> rows），支持点查与范围扫描。
 */
final class Partition {

    /** 每条索引记录的固定开销估算（桶节点、列表、引用等）。 */
    private static final long ENTRY_OVERHEAD_BYTES = 40L;

    private final String id;
    private final long partitionKeyMin;
    private final long partitionKeyMax;
    private final NavigableMap<Long, List<Row>> index = new TreeMap<>();

    private long rowCount;
    private long indexSizeBytes;

    Partition(String id, long partitionKeyMin, long partitionKeyMax) {
        this.id = id;
        this.partitionKeyMin = partitionKeyMin;
        this.partitionKeyMax = partitionKeyMax;
    }

    String id() {
        return id;
    }

    /** 该分区覆盖的分区键区间是否与 [from, to] 相交。 */
    boolean intersects(long from, long to) {
        return partitionKeyMin <= to && from <= partitionKeyMax;
    }

    void put(Row row) {
        index.computeIfAbsent(row.sortKey(), key -> new ArrayList<>()).add(row);
        rowCount++;
        indexSizeBytes += ENTRY_OVERHEAD_BYTES + (long) row.payload().length() * 2L;
    }

    /** 索引键点查。 */
    List<Row> pointLookup(long sortKey) {
        List<Row> rows = index.get(sortKey);
        return rows == null ? List.of() : List.copyOf(rows);
    }

    /** 索引键闭区间范围扫描，结果按 sortKey 有序。 */
    List<Row> rangeScan(long from, long to) {
        return index.subMap(from, true, to, true).values().stream()
                .flatMap(List::stream)
                .toList();
    }

    long rowCount() {
        return rowCount;
    }

    /** 清空本分区索引。删除分区时调用，确保不留下数据与索引残留。 */
    void clear() {
        index.clear();
        rowCount = 0;
        indexSizeBytes = 0;
    }

    PartitionStats stats() {
        OptionalLong min = index.isEmpty() ? OptionalLong.empty() : OptionalLong.of(index.firstKey());
        OptionalLong max = index.isEmpty() ? OptionalLong.empty() : OptionalLong.of(index.lastKey());
        return new PartitionStats(id, partitionKeyMin, partitionKeyMax,
                rowCount, indexSizeBytes, min, max);
    }
}
