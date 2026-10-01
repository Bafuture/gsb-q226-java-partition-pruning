package com.example.gsb.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PartitionSchemeTest {

    @Test
    @DisplayName("日期分区：partitionOf 与 boundsOf 往返一致（按月）")
    void monthlySchemeRoundTrips() {
        DateRangePartitionScheme scheme = DateRangePartitionScheme.monthly();
        long key = LocalDate.of(2024, 3, 15).toEpochDay();

        String partitionId = scheme.partitionOf(key);
        assertThat(partitionId).isEqualTo("2024-03");

        KeyRange bounds = scheme.boundsOf(partitionId);
        assertThat(bounds.contains(key)).isTrue();
        assertThat(bounds).isEqualTo(new KeyRange(
                LocalDate.of(2024, 3, 1).toEpochDay(),
                LocalDate.of(2024, 4, 1).toEpochDay()));
    }

    @Test
    @DisplayName("日期分区：partitionOf 与 boundsOf 往返一致（按天）")
    void dailySchemeRoundTrips() {
        DateRangePartitionScheme scheme = DateRangePartitionScheme.daily();
        long key = LocalDate.of(2024, 12, 31).toEpochDay();

        String partitionId = scheme.partitionOf(key);
        assertThat(partitionId).isEqualTo("2024-12-31");
        assertThat(scheme.boundsOf(partitionId)).isEqualTo(new KeyRange(key, key + 1));
    }

    @Test
    @DisplayName("哈希分区：键均匀落入固定桶，等值精确命中单桶")
    void hashSchemeDistributesAndHitsSingleBucket() {
        HashPartitionScheme scheme = new HashPartitionScheme(8);

        Set<String> seen = new HashSet<>();
        for (long key = 0; key < 10_000; key++) {
            String id = scheme.partitionOf(key);
            assertThat(id).startsWith("bucket-");
            seen.add(id);
            assertThat(scheme.candidatesForEqual(key)).containsExactly(id);
        }
        assertThat(seen).hasSize(8);
    }

    @Test
    @DisplayName("哈希分区：桶边界是全域，范围查询无法裁剪")
    void hashSchemeBoundsCoverEverything() {
        HashPartitionScheme scheme = new HashPartitionScheme(4);
        assertThat(scheme.boundsOf("bucket-0")).isEqualTo(KeyRange.all());
        assertThat(scheme.boundsOf("bucket-0").overlaps(new KeyRange(5, 6))).isTrue();
    }

    @Test
    @DisplayName("非法参数：桶数必须为正，键范围起点不能大于终点")
    void invalidArgumentsRejected() {
        assertThatThrownBy(() -> new HashPartitionScheme(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KeyRange(10, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
