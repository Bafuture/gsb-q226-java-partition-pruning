package com.example.gsb.partition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 需求 7：同一份数据、同一条范围查询，对比不同分区键选择下的裁剪效果。
 *
 * <p>数据：2024 全年 366 天，每天 20 行，共 7320 行。
 * 查询：2024-06-10 ~ 2024-06-20（10 天，400 行）。
 */
class PartitionKeyChoiceComparisonTest {

    private static final LocalDate START = LocalDate.of(2024, 1, 1);
    private static final int DAYS = 366;
    private static final int ROWS_PER_DAY = 20;

    private static final long QUERY_FROM = LocalDate.of(2024, 6, 10).toEpochDay();
    private static final long QUERY_TO = LocalDate.of(2024, 6, 20).toEpochDay();

    private PartitionedTable monthly;
    private PartitionedTable daily;
    private PartitionedTable hashed;

    @BeforeEach
    void setUp() {
        monthly = new PartitionedTable(DateRangePartitionScheme.monthly(), Row::ts);
        daily = new PartitionedTable(DateRangePartitionScheme.daily(), Row::ts);
        hashed = new PartitionedTable(new HashPartitionScheme(16), Row::ts);
        for (int d = 0; d < DAYS; d++) {
            long epochDay = START.plusDays(d).toEpochDay();
            for (int n = 0; n < ROWS_PER_DAY; n++) {
                Row row = new Row("evt-%03d-%02d".formatted(d, n), epochDay, "p" + d + "-" + n);
                monthly.put(row);
                daily.put(row);
                hashed.put(row);
            }
        }
    }

    private static long rowsExamined(PartitionedTable table, List<String> candidateIds) {
        Set<String> ids = new HashSet<>(candidateIds);
        return table.stats().stream()
                .filter(s -> ids.contains(s.partitionId()))
                .mapToLong(PartitionStats::rowCount)
                .sum();
    }

    @Test
    @DisplayName("对比：分区键与查询谓词对齐时裁剪生效，哈希分区键无法裁剪范围查询")
    void partitionKeyChoiceDeterminesPruningEffectiveness() {
        PartitionQuery query = PartitionQuery.between(QUERY_FROM, QUERY_TO);

        PruningReport monthlyReport = monthly.plan(query);
        PruningReport dailyReport = daily.plan(query);
        PruningReport hashReport = hashed.plan(query);

        long monthlyRows = rowsExamined(monthly, monthlyReport.candidatePartitions());
        long dailyRows = rowsExamined(daily, dailyReport.candidatePartitions());
        long hashRows = rowsExamined(hashed, hashReport.candidatePartitions());

        System.out.printf("%-28s %12s %12s %14s%n",
                "分区键选择", "裁剪前分区", "裁剪后分区", "候选分区行数");
        printRow("按月日期（对齐+粗粒度）", monthlyReport, monthlyRows);
        printRow("按天日期（对齐+细粒度）", dailyReport, dailyRows);
        printRow("按时间哈希（不对齐）", hashReport, hashRows);

        // 按月：查询落在 6 月内，只扫 1 个分区（600 行），裁掉 11 个。
        assertThat(monthlyReport.scannedBefore()).isEqualTo(12);
        assertThat(monthlyReport.candidatePartitions()).containsExactly("2024-06");
        assertThat(monthlyRows).isEqualTo(30L * ROWS_PER_DAY);

        // 按天：粒度更细，候选分区更多（10 个），但每个分区更小，
        // 候选分区总行数 200 < 按月 600，扫描放大更小。
        assertThat(dailyReport.candidatePartitions()).hasSize(10);
        assertThat(dailyRows).isEqualTo(10L * ROWS_PER_DAY);
        assertThat(dailyRows).isLessThan(monthlyRows);

        // 哈希：范围谓词无法裁剪，16 个桶全部保留，扫描全部 7320 行。
        assertThat(hashReport.scannedAfter()).isEqualTo(hashed.partitionCount());
        assertThat(hashReport.prunedCount()).isZero();
        assertThat(hashRows).isEqualTo((long) DAYS * ROWS_PER_DAY);

        // 三种方案查询结果必须一致（裁剪只影响代价，不影响正确性）。
        List<Row> expected = monthly.scan(query).rows();
        assertThat(expected).hasSize(10 * ROWS_PER_DAY);
        assertThat(daily.scan(query).rows()).isEqualTo(expected);
        assertThat(hashed.scan(query).rows()).isEqualTo(expected);
    }

    @Test
    @DisplayName("对比：等值谓词下哈希分区键同样能精确裁剪")
    void hashPartitionKeyPrunesEqualityPredicates() {
        PruningReport report = hashed.plan(PartitionQuery.equalTo(QUERY_FROM));

        assertThat(report.scannedBefore()).isEqualTo(16);
        assertThat(report.scannedAfter()).isEqualTo(1);
        assertThat(report.prunedCount()).isEqualTo(15);
    }

    private static void printRow(String label, PruningReport report, long rows) {
        System.out.printf("%-28s %12d %12d %14d%n",
                label, report.scannedBefore(), report.scannedAfter(), rows);
    }
}
