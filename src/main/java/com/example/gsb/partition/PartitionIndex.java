package com.example.gsb.partition;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 单个分区内的主键索引：基于有序映射实现，按主键 {@link Row#id()} 排序。
 *
 * <p>支持 O(log n) 点查、按主键区间的范围扫描，并增量维护索引占用大小的估计值。
 * 分区被删除时调用 {@link #clear()} 释放全部索引数据。
 */
final class PartitionIndex {

    /** 每条索引项固定开销的估计字节数（TreeMap 节点 + 引用等）。 */
    private static final long ENTRY_OVERHEAD_BYTES = 48L;
    private static final long CHAR_BYTES = 2L;

    private final NavigableMap<String, Row> byId = new TreeMap<>();
    private long sizeBytes;

    /** 插入或覆盖同一主键的行。 */
    void put(Row row) {
        Row previous = byId.put(row.id(), row);
        if (previous != null) {
            sizeBytes -= entrySize(previous);
        }
        sizeBytes += entrySize(row);
    }

    Row get(String id) {
        return byId.get(id);
    }

    /**
     * 按主键区间扫描；任一端点为 null 表示不限制。
     *
     * @param fromInclusive 起始主键（含），null 表示从头开始
     * @param toExclusive   结束主键（不含），null 表示扫到末尾
     */
    List<Row> scan(String fromInclusive, String toExclusive) {
        NavigableMap<String, Row> view = byId;
        if (fromInclusive != null) {
            view = view.tailMap(fromInclusive, true);
        }
        if (toExclusive != null) {
            view = view.headMap(toExclusive, false);
        }
        return new ArrayList<>(view.values());
    }

    int rowCount() {
        return byId.size();
    }

    long sizeBytes() {
        return sizeBytes;
    }

    void clear() {
        byId.clear();
        sizeBytes = 0L;
    }

    private static long entrySize(Row row) {
        return ENTRY_OVERHEAD_BYTES
                + (long) row.id().length() * CHAR_BYTES
                + (long) row.payload().length() * CHAR_BYTES
                + Long.BYTES;
    }
}
