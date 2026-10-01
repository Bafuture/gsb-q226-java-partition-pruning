package com.example.gsb.partition;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * 分区裁剪效果演示：同一份事件数据、同一条“查最近一个月”的查询，
 * 对比不同分区键选择（按月日期、按天日期、按时间哈希）下的裁剪效果，
 * 以及粗/细粒度对“扫描分区数 vs 每分区扫描行数”的影响。
 *
 * <p>运行：{@code mvn -q exec:java ...} 不方便（无 exec 插件），
 * 也可直接在测试 {@code PartitionKeyChoiceComparisonTest} 中查看断言与输出。
 */
public final class PruningDemo {

    private PruningDemo() {
    }

    public static void main(String[] args) {
        LocalDate start = LocalDate.of(2024, 1, 1);
        int days = 366;
        int rowsPerDay = 10;

        PartitionedTable monthly = new PartitionedTable(
                DateRangePartitionScheme.monthly(), Row::ts);
        PartitionedTable daily = new PartitionedTable(
                DateRangePartitionScheme.daily(), Row::ts);
        PartitionedTable hashed = new PartitionedTable(
                new HashPartitionScheme(16), Row::ts);
        seed(start, days, rowsPerDay, monthly, daily, hashed);

        LocalDate queryStart = LocalDate.of(2024, 11, 15);
        LocalDate queryEnd = queryStart.plusMonths(1);
        PartitionQuery query = PartitionQuery.between(
                queryStart.toEpochDay(), queryEnd.toEpochDay());

        printCase("按月日期分区（范围对齐，粒度粗）", monthly, query);
        printCase("按天日期分区（范围对齐，粒度细）", daily, query);
        printCase("按时间哈希分区（范围无法裁剪）", hashed, query);
    }

    private static void seed(LocalDate start, int days, int rowsPerDay,
                             PartitionedTable... tables) {
        for (int day = 0; day < days; day++) {
            long epochDay = start.plusDays(day).toEpochDay();
            for (int n = 0; n < rowsPerDay; n++) {
                Row row = new Row(
                        "evt-%03d-%02d".formatted(day, n), epochDay,
                        "payload day " + day + " #" + n);
                for (PartitionedTable table : tables) {
                    table.put(row);
                }
            }
        }
    }

    private static void printCase(String title, PartitionedTable table, PartitionQuery query) {
        PruningReport report = table.plan(query);
        long rowsExamined = rowsIn(table, new HashSet<>(report.candidatePartitions()));
        System.out.println("== " + title + " ==");
        System.out.println("  方案: " + table.scheme());
        System.out.println("  " + report.summary());
        System.out.println("  候选分区内总行数(裁剪后仍需经索引过滤的行数): " + rowsExamined);
        // 触发一次真实扫描，确认结果行数
        ScanResult result = table.scan(query, Comparator.comparing(Row::id));
        System.out.println("  实际返回行数: " + result.rows().size());
        System.out.println();
    }

    private static long rowsIn(PartitionedTable table, Set<String> partitionIds) {
        return table.stats().stream()
                .filter(s -> partitionIds.contains(s.partitionId()))
                .mapToLong(PartitionStats::rowCount)
                .sum();
    }
}
