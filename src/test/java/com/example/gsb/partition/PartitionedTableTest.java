package com.example.gsb.partition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartitionedTableTest {

    /** 以 epoch day 作为分区键，30 天一个分区（相当于按月分区）。 */
    private static PartitionedTable monthlyTable() {
        return new PartitionedTable(new RangePartitioner(30));
    }

    private static Row row(long rowId, long day, long sortKey) {
        return new Row(rowId, day, sortKey, "payload-" + rowId);
    }

    @Test
    @DisplayName("写入：按分区键路由到各分区，每分区维护独立索引")
    void writeRoutesRowsToOwnPartitions() {
        PartitionedTable table = monthlyTable();
        for (long day = 0; day < 90; day++) {
            table.write(row(day, day, day * 100));
        }

        assertThat(table.partitionCount()).isEqualTo(3);
        assertThat(table.stats())
                .extracting(PartitionStats::rowCount)
                .containsExactly(30L, 30L, 30L);
        assertThat(table.stats())
                .extracting(PartitionStats::partitionKeyMin)
                .containsExactly(0L, 30L, 60L);
        assertThat(table.stats())
                .extracting(PartitionStats::partitionKeyMax)
                .containsExactly(29L, 59L, 89L);
    }

    @Test
    @DisplayName("裁剪：分区键范围条件排除无关分区，并给出前后分区数对比")
    void pruningExcludesIrrelevantPartitions() {
        PartitionedTable table = monthlyTable();
        for (long day = 0; day < 120; day++) {
            table.write(row(day, day, day));
        }

        QueryResult result = table.query(Query.builder()
                .partitionKeyRange(35, 65)
                .build());

        assertThat(result.partitionsBeforePruning()).isEqualTo(4);
        assertThat(result.partitionsAfterPruning()).isEqualTo(2);
        assertThat(result.prunedPartitions()).isEqualTo(2);
        assertThat(result.scannedPartitions()).containsExactly("range-1", "range-2");
        assertThat(result.rows())
                .allSatisfy(r -> assertThat(r.partitionKey()).isBetween(35L, 65L));
        assertThat(result.rows()).hasSize(31);

        QueryResult unpruned = table.query(Query.all());
        assertThat(unpruned.partitionsAfterPruning())
                .isEqualTo(unpruned.partitionsBeforePruning())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("分区内查询：裁剪后在候选分区内点查与范围查询")
    void pointAndRangeQueryInsidePartition() {
        PartitionedTable table = monthlyTable();
        List<Row> expected = new ArrayList<>();
        for (long sortKey = 1; sortKey <= 10; sortKey++) {
            Row row = row(sortKey, 5, sortKey);
            expected.add(row);
            table.write(row);
        }
        table.write(row(100, 5, 5)); // 同一索引键上的第二行

        QueryResult point = table.query(Query.builder()
                .partitionKeyRange(0, 29)
                .sortKeyPoint(5)
                .build());
        assertThat(point.partitionsAfterPruning()).isEqualTo(1);
        assertThat(point.rows()).extracting(Row::sortKey).containsExactly(5L, 5L);

        QueryResult range = table.query(Query.builder()
                .partitionKeyRange(0, 29)
                .sortKeyRange(3, 6)
                .build());
        assertThat(range.rows()).extracting(Row::sortKey)
                .containsExactly(3L, 4L, 5L, 5L, 6L);
    }

    @Test
    @DisplayName("删除分区：索引一并清理，不留下悬挂引用")
    void dropPartitionClearsIndexAndReferences() {
        PartitionedTable table = monthlyTable();
        for (long day = 0; day < 90; day++) {
            table.write(row(day, day, day));
        }
        String droppedId = table.partitionIds().get(1);

        assertThat(table.dropPartition(droppedId)).isTrue();
        assertThat(table.dropPartition(droppedId)).isFalse();

        assertThat(table.partitionIds()).doesNotContain(droppedId);
        assertThat(table.stats()).extracting(PartitionStats::partitionId)
                .doesNotContain(droppedId);

        QueryResult result = table.query(Query.builder()
                .partitionKeyRange(30, 59)
                .build());
        assertThat(result.partitionsAfterPruning()).isZero();
        assertThat(result.rows()).isEmpty();

        // 重新创建同名分区：应为空分区，证明旧索引已彻底清理
        table.addPartition(40);
        assertThat(table.stats())
                .filteredOn(s -> s.partitionId().equals(droppedId))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.rowCount()).isZero();
                    assertThat(s.indexSizeBytes()).isZero();
                    assertThat(s.sortKeyMin()).isEmpty();
                    assertThat(s.sortKeyMax()).isEmpty();
                });
    }

    @Test
    @DisplayName("跨分区合并：结果按指定键有序并按 rowId 去重")
    void crossPartitionMergeIsSortedAndDeduplicated() {
        PartitionedTable table = monthlyTable();
        // 交错写入三个分区，sortKey 跨分区交错
        table.write(row(1, 0, 10));
        table.write(row(2, 40, 20));
        table.write(row(3, 80, 30));
        table.write(row(4, 0, 40));
        table.write(row(5, 40, 50));
        // 同一 rowId 出现在两个分区（例如重分区迁移期间的重复），合并时必须去重
        table.write(row(6, 0, 60));
        table.write(row(6, 80, 60));

        QueryResult result = table.query(Query.all());

        assertThat(result.rows()).extracting(Row::rowId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(result.rows()).extracting(Row::sortKey)
                .containsExactly(10L, 20L, 30L, 40L, 50L, 60L);
        assertThat(result.rows()).isSortedAccordingTo(
                java.util.Comparator.comparingLong(Row::sortKey).thenComparingLong(Row::rowId));
    }

    @Test
    @DisplayName("统计：每个分区的键范围、条数与索引大小")
    void statsReportKeyRangeCountAndIndexSize() {
        PartitionedTable table = monthlyTable();
        table.write(new Row(1, 5, 100, "aaaa"));
        table.write(new Row(2, 5, 200, "bbbbbbbb"));
        table.write(new Row(3, 5, 300, "cc"));

        assertThat(table.stats()).singleElement().satisfies(s -> {
            assertThat(s.partitionKeyMin()).isEqualTo(0);
            assertThat(s.partitionKeyMax()).isEqualTo(29);
            assertThat(s.rowCount()).isEqualTo(3);
            assertThat(s.sortKeyMin()).hasValue(100);
            assertThat(s.sortKeyMax()).hasValue(300);
            assertThat(s.indexSizeBytes()).isPositive();
        });

        long before = table.stats().get(0).indexSizeBytes();
        table.write(new Row(4, 5, 400, "dddddddddd"));
        assertThat(table.stats().get(0).indexSizeBytes()).isGreaterThan(before);
    }
}
