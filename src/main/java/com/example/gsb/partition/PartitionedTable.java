package com.example.gsb.partition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * 分区表：按分区策略把行路由到不同分区，每个分区维护自己的索引；
 * 查询时先按分区键条件做分区裁剪，再只在候选分区内执行点查或范围查询，
 * 最后对跨分区结果做归并、去重与排序。
 */
public final class PartitionedTable {

    /** 默认的结果排序：按主键升序（与归并顺序一致，无需二次排序）。 */
    public static final Comparator<Row> ORDER_BY_ID = Comparator.comparing(Row::id);

    private final PartitionScheme scheme;
    private final ToLongFunction<Row> partitionKeyExtractor;
    private final Map<String, Partition> partitions = new LinkedHashMap<>();

    /**
     * @param scheme                分区策略（日期范围 / 哈希等）
     * @param partitionKeyExtractor 从行中提取分区键
     */
    public PartitionedTable(PartitionScheme scheme, ToLongFunction<Row> partitionKeyExtractor) {
        this.scheme = Objects.requireNonNull(scheme, "scheme must not be null");
        this.partitionKeyExtractor =
                Objects.requireNonNull(partitionKeyExtractor, "partitionKeyExtractor must not be null");
    }

    /** 写入一行；目标分区不存在时自动创建（分区新增）。 */
    public void put(Row row) {
        Objects.requireNonNull(row, "row must not be null");
        long key = partitionKeyExtractor.applyAsLong(row);
        String partitionId = scheme.partitionOf(key);
        partitions.computeIfAbsent(partitionId, id -> new Partition(id, scheme.boundsOf(id)))
                .put(row);
    }

    public void putAll(Iterable<Row> rows) {
        for (Row row : rows) {
            put(row);
        }
    }

    /**
     * 点查：按分区键精确定位到单个分区，再在分区内做主键查找。
     *
     * @return 命中则返回行，否则返回 null
     */
    public Row get(String id, long partitionKey) {
        Partition partition = partitions.get(scheme.partitionOf(partitionKey));
        return partition == null ? null : partition.get(id);
    }

    /**
     * 分区裁剪：根据查询条件中的分区键范围/等值，从全部分区中筛出候选分区。
     * 返回的 {@link PruningReport} 给出裁剪前后扫描分区数的对比。
     */
    public PruningReport plan(PartitionQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        List<String> all = new ArrayList<>(partitions.keySet());
        Set<String> candidates = switch (query.kind()) {
            case EQUAL -> scheme.candidatesForEqual(query.equalKey());
            case RANGE -> {
                Set<String> hits = new java.util.HashSet<>();
                for (String partitionId : all) {
                    if (scheme.boundsOf(partitionId).overlaps(query.range())) {
                        hits.add(partitionId);
                    }
                }
                yield hits;
            }
        };
        List<String> candidateList = new ArrayList<>();
        List<String> prunedList = new ArrayList<>();
        for (String partitionId : all) {
            (candidates.contains(partitionId) ? candidateList : prunedList).add(partitionId);
        }
        return new PruningReport(all.size(), candidateList, prunedList);
    }

    /** 按默认主键顺序执行范围/等值查询。 */
    public ScanResult scan(PartitionQuery query) {
        return scan(query, ORDER_BY_ID);
    }

    /**
     * 执行查询：裁剪 → 候选分区内主键区间扫描 → 跨分区归并去重 → 按指定键排序。
     *
     * @param orderBy 结果排序键；传 {@link #ORDER_BY_ID} 时直接利用归并顺序，零额外排序
     */
    public ScanResult scan(PartitionQuery query, Comparator<Row> orderBy) {
        Objects.requireNonNull(orderBy, "orderBy must not be null");
        PruningReport report = plan(query);
        List<List<Row>> perPartition = new ArrayList<>(report.candidatePartitions().size());
        for (String partitionId : report.candidatePartitions()) {
            Partition partition = partitions.get(partitionId);
            if (partition != null) {
                perPartition.add(partition.scan(query.fromIdInclusive(), query.toIdExclusive()));
            }
        }
        List<Row> merged = KWayMerger.mergeByIdDedup(perPartition);
        merged.removeIf(row -> !matchesPartitionKey(row, query));
        if (!ORDER_BY_ID.equals(orderBy)) {
            merged.sort(orderBy);
        }
        return new ScanResult(merged, report);
    }

    /**
     * 行级分区键过滤：分区裁剪只能保证分区“可能”含相关行（查询范围落在分区内部、
     * 或哈希分区无法表达离散键），因此归并后仍需对每一行做一次谓词校验。
     */
    private boolean matchesPartitionKey(Row row, PartitionQuery query) {
        long key = partitionKeyExtractor.applyAsLong(row);
        return switch (query.kind()) {
            case EQUAL -> key == query.equalKey();
            case RANGE -> query.range().contains(key);
        };
    }

    /**
     * 删除分区：从表中摘除并清空其索引，之后任何查询都不会再引用到它。
     *
     * @return 分区存在并被删除返回 true，否则返回 false
     */
    public boolean dropPartition(String partitionId) {
        Partition removed = partitions.remove(partitionId);
        if (removed == null) {
            return false;
        }
        removed.drop();
        return true;
    }

    /** 全部分区的元数据统计（按分区 id 排序）。 */
    public List<PartitionStats> stats() {
        return partitions.values().stream()
                .map(Partition::stats)
                .sorted(Comparator.comparing(PartitionStats::partitionId))
                .toList();
    }

    public int partitionCount() {
        return partitions.size();
    }

    public Set<String> partitionIds() {
        return Set.copyOf(partitions.keySet());
    }

    public PartitionScheme scheme() {
        return scheme;
    }
}
