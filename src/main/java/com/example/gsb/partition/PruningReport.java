package com.example.gsb.partition;

import java.util.List;
import java.util.Objects;

/**
 * 分区裁剪报告：给出裁剪前后需要扫描的分区数量与分区 id 列表。
 *
 * <ul>
 *   <li>{@link #scannedBefore()}：不做裁剪时需扫描的分区数（全部已有分区）；</li>
 *   <li>{@link #scannedAfter()}：裁剪后候选分区数；</li>
 *   <li>{@link #prunedCount()}：被排除的分区数；</li>
 *   <li>{@link #pruningRatio()}：被排除分区占比。</li>
 * </ul>
 */
public record PruningReport(int scannedBefore,
                            List<String> candidatePartitions,
                            List<String> prunedPartitions) {

    public PruningReport {
        Objects.requireNonNull(candidatePartitions, "candidatePartitions must not be null");
        Objects.requireNonNull(prunedPartitions, "prunedPartitions must not be null");
        candidatePartitions = List.copyOf(candidatePartitions);
        prunedPartitions = List.copyOf(prunedPartitions);
    }

    public int scannedAfter() {
        return candidatePartitions.size();
    }

    public int prunedCount() {
        return prunedPartitions.size();
    }

    public double pruningRatio() {
        return scannedBefore == 0 ? 0.0 : (double) prunedCount() / scannedBefore;
    }

    public String summary() {
        return "partitions scanned before pruning=" + scannedBefore
                + ", after pruning=" + scannedAfter()
                + ", pruned=" + prunedCount()
                + " (" + String.format("%.1f%%", pruningRatio() * 100) + ")";
    }
}
