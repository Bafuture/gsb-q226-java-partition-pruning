package com.example.gsb.partition;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * 按日期范围分区：分区键是 epoch day，每个分区覆盖一天或一个自然月。
 *
 * <p>分区 id 直接采用可读的日期字面量：按天为 {@code "2024-03-15"}，按月为
 * {@code "2024-03"}，因此 id 可以无损解析回键范围，便于范围裁剪。
 */
public final class DateRangePartitionScheme implements PartitionScheme {

    public enum Granularity {
        DAY,
        MONTH
    }

    private final Granularity granularity;

    private DateRangePartitionScheme(Granularity granularity) {
        this.granularity = granularity;
    }

    public static DateRangePartitionScheme daily() {
        return new DateRangePartitionScheme(Granularity.DAY);
    }

    public static DateRangePartitionScheme monthly() {
        return new DateRangePartitionScheme(Granularity.MONTH);
    }

    public Granularity granularity() {
        return granularity;
    }

    @Override
    public String partitionOf(long epochDay) {
        LocalDate date = LocalDate.ofEpochDay(epochDay);
        return switch (granularity) {
            case DAY -> date.toString();
            case MONTH -> YearMonth.from(date).toString();
        };
    }

    @Override
    public KeyRange boundsOf(String partitionId) {
        Objects.requireNonNull(partitionId, "partitionId must not be null");
        LocalDate start = switch (granularity) {
            case DAY -> LocalDate.parse(partitionId);
            case MONTH -> YearMonth.parse(partitionId).atDay(1);
        };
        LocalDate end = switch (granularity) {
            case DAY -> start.plusDays(1);
            case MONTH -> start.plusMonths(1);
        };
        return new KeyRange(start.toEpochDay(), end.toEpochDay());
    }

    @Override
    public String toString() {
        return "DateRangePartitionScheme[" + granularity + "]";
    }
}
