package com.example.gsb.partition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartitionedTableTest {

    private PartitionedTable table;

    private static Row row(String id, String date) {
        return new Row(id, LocalDate.parse(date).toEpochDay(), "payload-" + id);
    }

    private static long day(String date) {
        return LocalDate.parse(date).toEpochDay();
    }

    @BeforeEach
    void setUp() {
        table = new PartitionedTable(DateRangePartitionScheme.monthly(), Row::ts);
    }

    @Test
    @DisplayName("分区写入：按分区键路由到对应分区，分区自动创建")
    void writeRoutesRowsIntoDatePartitions() {
        table.put(row("a", "2024-01-05"));
        table.put(row("b", "2024-01-20"));
        table.put(row("c", "2024-02-01"));
        table.put(row("d", "2024-03-15"));

        assertThat(table.partitionCount()).isEqualTo(3);
        assertThat(table.partitionIds()).containsExactlyInAnyOrder("2024-01", "2024-02", "2024-03");
        assertThat(table.stats())
                .extracting(PartitionStats::partitionId, PartitionStats::rowCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("2024-01", 2),
                        org.assertj.core.groups.Tuple.tuple("2024-02", 1),
                        org.assertj.core.groups.Tuple.tuple("2024-03", 1));
    }

    @Test
    @DisplayName("裁剪生效：范围查询只保留相交分区，报告给出前后对比")
    void rangeQueryPrunesUnrelatedPartitions() {
        for (int month = 1; month <= 12; month++) {
            table.put(row("m" + month, "2024-%02d-10".formatted(month)));
        }

        PruningReport report = table.plan(
                PartitionQuery.between(day("2024-03-01"), day("2024-05-01")));

        assertThat(report.scannedBefore()).isEqualTo(12);
        assertThat(report.scannedAfter()).isEqualTo(2);
        assertThat(report.candidatePartitions()).containsExactlyInAnyOrder("2024-03", "2024-04");
        assertThat(report.prunedPartitions()).hasSize(10);
        assertThat(report.summary()).contains("before pruning=12", "after pruning=2");
    }

    @Test
    @DisplayName("裁剪生效：等值查询精确命中单个分区")
    void equalQueryHitsExactlyOnePartition() {
        for (int month = 1; month <= 6; month++) {
            table.put(row("m" + month, "2024-%02d-10".formatted(month)));
        }

        PruningReport report = table.plan(PartitionQuery.equalTo(day("2024-04-15")));

        assertThat(report.scannedBefore()).isEqualTo(6);
        assertThat(report.candidatePartitions()).containsExactly("2024-04");
        assertThat(report.prunedCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("分区内查询：点查命中/未命中，主键区间范围查询")
    void pointAndRangeQueryWithinPartitions() {
        table.put(row("u001", "2024-01-03"));
        table.put(row("u002", "2024-01-10"));
        table.put(row("u003", "2024-01-20"));
        table.put(row("u101", "2024-02-02"));

        assertThat(table.get("u002", day("2024-01-10")).payload()).isEqualTo("payload-u002");
        assertThat(table.get("u002", day("2024-02-01"))).isNull();
        assertThat(table.get("nope", day("2024-01-10"))).isNull();

        ScanResult result = table.scan(PartitionQuery
                .between(day("2024-01-01"), day("2024-03-01"))
                .withIdRange("u001", "u003"));
        assertThat(result.rows()).extracting(Row::id).containsExactly("u001", "u002");

        ScanResult allInRange = table.scan(
                PartitionQuery.between(day("2024-01-01"), day("2024-03-01")));
        assertThat(allInRange.rows()).extracting(Row::id)
                .containsExactly("u001", "u002", "u003", "u101");
        assertThat(result.pruning().candidatePartitions())
                .containsExactlyInAnyOrder("2024-01", "2024-02");
    }

    @Test
    @DisplayName("分区删除：索引一并清理，无悬挂引用，同 id 分区可干净重建")
    void dropPartitionCleansUpIndexAndReferences() {
        table.put(row("a", "2024-01-05"));
        table.put(row("b", "2024-01-06"));
        table.put(row("c", "2024-02-01"));
        long janIndexSize = table.stats().stream()
                .filter(s -> s.partitionId().equals("2024-01"))
                .findFirst().orElseThrow().indexSizeBytes();
        assertThat(janIndexSize).isPositive();

        assertThat(table.dropPartition("2024-01")).isTrue();

        assertThat(table.partitionCount()).isEqualTo(1);
        assertThat(table.partitionIds()).containsExactly("2024-02");
        assertThat(table.stats()).extracting(PartitionStats::partitionId)
                .containsExactly("2024-02");
        assertThat(table.get("a", day("2024-01-05"))).isNull();
        ScanResult scan = table.scan(PartitionQuery.all());
        assertThat(scan.pruning().candidatePartitions()).containsExactly("2024-02");
        assertThat(scan.rows()).extracting(Row::id).containsExactly("c");

        assertThat(table.dropPartition("2024-01")).isFalse();

        table.put(row("z", "2024-01-10"));
        assertThat(table.stats()).extracting(PartitionStats::partitionId, PartitionStats::rowCount)
                .contains(org.assertj.core.groups.Tuple.tuple("2024-01", 1));
        assertThat(table.get("a", day("2024-01-05"))).isNull();
        assertThat(table.get("z", day("2024-01-10"))).isNotNull();
    }

    @Test
    @DisplayName("跨分区合并：结果按主键有序，同主键去重保留最新版本")
    void crossPartitionMergeDeduplicatesAndOrders() {
        table.put(row("k1", "2024-01-01"));
        table.put(row("k2", "2024-01-02"));
        table.put(row("k1", "2024-02-01"));
        table.put(row("k3", "2024-02-02"));
        table.put(row("k2", "2024-03-01"));

        ScanResult result = table.scan(PartitionQuery.all());

        assertThat(result.rows()).extracting(Row::id).containsExactly("k1", "k2", "k3");
        assertThat(result.rows()).extracting(Row::ts)
                .containsExactly(day("2024-02-01"), day("2024-03-01"), day("2024-02-02"));
    }

    @Test
    @DisplayName("跨分区查询：结果可按指定键排序")
    void crossPartitionQueryOrderedBySpecifiedKey() {
        table.put(row("a", "2024-01-01"));
        table.put(row("b", "2024-02-01"));
        table.put(row("c", "2024-03-01"));

        ScanResult byTsDesc = table.scan(PartitionQuery.all(),
                Comparator.comparingLong(Row::ts).reversed());
        assertThat(byTsDesc.rows()).extracting(Row::id).containsExactly("c", "b", "a");

        ScanResult byId = table.scan(PartitionQuery.all(), PartitionedTable.ORDER_BY_ID);
        assertThat(byId.rows()).extracting(Row::id).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("分区统计：键范围、条数与索引大小")
    void statsExposeKeyRangeRowCountAndIndexSize() {
        table.put(row("a", "2024-01-05"));
        table.put(row("b", "2024-01-20"));
        table.put(row("c", "2024-02-01"));

        List<PartitionStats> stats = table.stats();
        assertThat(stats).hasSize(2);

        PartitionStats jan = stats.get(0);
        assertThat(jan.partitionId()).isEqualTo("2024-01");
        assertThat(jan.keyRange()).isEqualTo(new KeyRange(day("2024-01-01"), day("2024-02-01")));
        assertThat(jan.rowCount()).isEqualTo(2);
        assertThat(jan.indexSizeBytes()).isPositive();

        PartitionStats feb = stats.get(1);
        assertThat(feb.keyRange().contains(day("2024-02-15"))).isTrue();
        assertThat(feb.rowCount()).isEqualTo(1);

        long before = jan.indexSizeBytes();
        table.put(row("d", "2024-01-25"));
        long after = table.stats().get(0).indexSizeBytes();
        assertThat(after).isGreaterThan(before);
    }
}
