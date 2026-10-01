package com.example.gsb.partition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分区表：按 {@link Partitioner} 把数据路由到各分区，每个分区持有独立索引。
 *
 * <p>查询时先依据分区键条件裁剪分区，再在候选分区内做点查/范围查，
 * 最后跨分区合并、按 rowId 去重、按指定键（sortKey, rowId）排序。
 */
public final class PartitionedTable {

    private final Partitioner partitioner;
    private final Map<String, Partition> partitions = new LinkedHashMap<>();

    public PartitionedTable(Partitioner partitioner) {
        this.partitioner = partitioner;
    }

    /**
     * 显式新增“将容纳该分区键”的分区（幂等）。写入时若分区不存在也会自动创建。
     */
    public String addPartition(long anyPartitionKeyInIt) {
        String id = partitioner.partitionIdFor(anyPartitionKeyInIt);
        partitions.computeIfAbsent(id, key -> new Partition(
                key,
                partitioner.keyMinOf(anyPartitionKeyInIt),
                partitioner.keyMaxOf(anyPartitionKeyInIt)));
        return id;
    }

    /**
     * 删除分区：从表中摘除引用，并清空该分区索引，避免悬挂引用与数据残留。
     *
     * @return 是否删除成功（不存在返回 false）
     */
    public boolean dropPartition(String partitionId) {
        Partition removed = partitions.remove(partitionId);
        if (removed == null) {
            return false;
        }
        removed.clear();
        return true;
    }

    /** 写入一行，按分区键路由到对应分区。 */
    public String write(Row row) {
        String id = addPartition(row.partitionKey());
        partitions.get(id).put(row);
        return id;
    }

    /** 批量写入。 */
    public void writeAll(Iterable<Row> rows) {
        rows.forEach(this::write);
    }

    public int partitionCount() {
        return partitions.size();
    }

    public List<String> partitionIds() {
        return List.copyOf(partitions.keySet());
    }

    /**
     * 执行查询：分区裁剪 -> 分区内查询 -> 跨分区合并、去重、排序。
     */
    public QueryResult query(Query query) {
        List<Partition> candidates = prune(query);

        List<String> scannedIds = new ArrayList<>(candidates.size());
        List<Row> matched = new ArrayList<>();
        long from = query.sortKeyFromOrMin();
        long to = query.sortKeyToOrMax();
        Long partitionKeyPoint = query.partitionKeyPoint();
        long partitionKeyFrom = query.partitionKeyFrom() == null
                ? Long.MIN_VALUE : query.partitionKeyFrom();
        long partitionKeyTo = query.partitionKeyTo() == null
                ? Long.MAX_VALUE : query.partitionKeyTo();
        for (Partition partition : candidates) {
            scannedIds.add(partition.id());
            for (Row row : partition.rangeScan(from, to)) {
                // 裁剪只排除整分区；候选分区内仍需应用分区键谓词
                boolean matchesPartitionKey = partitionKeyPoint != null
                        ? row.partitionKey() == partitionKeyPoint
                        : row.partitionKey() >= partitionKeyFrom
                            && row.partitionKey() <= partitionKeyTo;
                if (matchesPartitionKey) {
                    matched.add(row);
                }
            }
        }

        matched.sort(Row.ORDER);
        Map<Long, Row> deduped = new LinkedHashMap<>();
        for (Row row : matched) {
            deduped.putIfAbsent(row.rowId(), row);
        }

        return new QueryResult(List.copyOf(deduped.values()),
                partitions.size(), candidates.size(), List.copyOf(scannedIds));
    }

    /**
     * 分区裁剪：
     * <ul>
     *   <li>分区键点查：哈希与范围策略都能定位到唯一分区；</li>
     *   <li>分区键范围：只保留键区间与之相交的分区
     *       （哈希分区的各桶覆盖整个键域，因此无法裁剪）；</li>
     *   <li>无分区键条件：扫描全部分区。</li>
     * </ul>
     */
    private List<Partition> prune(Query query) {
        Long point = query.partitionKeyPoint();
        if (point != null) {
            Partition partition = partitions.get(partitioner.partitionIdFor(point));
            return partition == null ? List.of() : List.of(partition);
        }

        long from = query.partitionKeyFrom() == null ? Long.MIN_VALUE : query.partitionKeyFrom();
        long to = query.partitionKeyTo() == null ? Long.MAX_VALUE : query.partitionKeyTo();

        List<Partition> candidates = new ArrayList<>();
        for (Partition partition : partitions.values()) {
            if (partition.intersects(from, to)) {
                candidates.add(partition);
            }
        }
        return candidates;
    }

    /** 全部分区的元数据统计。 */
    public List<PartitionStats> stats() {
        return partitions.values().stream().map(Partition::stats).toList();
    }
}
