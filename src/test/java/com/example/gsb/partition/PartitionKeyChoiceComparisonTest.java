package com.example.gsb.partition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 需求 7：对比不同分区键/策略对裁剪效果的影响。
 * 同一份数据分别按“日期范围”与“哈希”分区，比较同一查询裁剪前后扫描的分区数。
 */
class PartitionKeyChoiceComparisonTest {

    private static final int DAYS = 120;

    private static List<Row> dataset() {
        List<Row> rows = new ArrayList<>();
        for (long day = 0; day < DAYS; day++) {
            rows.add(new Row(day, day, day * 10, "event-" + day));
        }
        return rows;
    }

    @Test
    @DisplayName("范围查询：日期范围分区可裁剪，哈希分区无法裁剪")
    void rangeQueryPruningComparison() {
        PartitionedTable byRange = new PartitionedTable(new RangePartitioner(30));
        PartitionedTable byHash = new PartitionedTable(new HashPartitioner(4));
        byRange.writeAll(dataset());
        byHash.writeAll(dataset());

        Query rangeQuery = Query.builder().partitionKeyRange(10, 40).build();
        QueryResult rangeResult = byRange.query(rangeQuery);
        QueryResult hashResult = byHash.query(rangeQuery);

        // 日期范围分区：4 个分区只扫 2 个（裁剪掉 50%）
        assertThat(rangeResult.partitionsBeforePruning()).isEqualTo(4);
        assertThat(rangeResult.partitionsAfterPruning()).isEqualTo(2);
        // 哈希分区：每个桶都覆盖整个键域，范围条件无法裁剪，4 个分区全扫
        assertThat(hashResult.partitionsBeforePruning()).isEqualTo(4);
        assertThat(hashResult.partitionsAfterPruning()).isEqualTo(4);
        // 裁剪只影响扫描代价，不影响结果正确性
        assertThat(hashResult.rows()).isEqualTo(rangeResult.rows());
    }

    @Test
    @DisplayName("点查：哈希与范围分区都能裁剪到单个分区")
    void pointQueryPruningComparison() {
        PartitionedTable byRange = new PartitionedTable(new RangePartitioner(30));
        PartitionedTable byHash = new PartitionedTable(new HashPartitioner(4));
        byRange.writeAll(dataset());
        byHash.writeAll(dataset());

        Query pointQuery = Query.builder().partitionKeyPoint(42).build();

        QueryResult rangeResult = byRange.query(pointQuery);
        assertThat(rangeResult.partitionsAfterPruning()).isEqualTo(1);
        assertThat(rangeResult.rows()).extracting(Row::partitionKey).containsExactly(42L);

        QueryResult hashResult = byHash.query(pointQuery);
        assertThat(hashResult.partitionsAfterPruning()).isEqualTo(1);
        assertThat(hashResult.rows()).extracting(Row::partitionKey).containsExactly(42L);
    }
}
