package evernight.client.utils.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultLootLogicTest {
    @ParameterizedTest
    @CsvSource({
            "0,30", "1,30", "10,30",
            "11,60", "20,60",
            "21,120", "40,120",
            "41,200", "60,200",
            "61,400", "120,400",
            "121,600", "600,1000", "601,1000", "100000,1000"
    })
    @DisplayName("windowForSeconds 在 10/20/40/60 秒边界切换，并在 1000 封顶")
    void windowForSecondsBoundaries(long seconds, int expected) {
        assertEquals(expected, VaultLootLogic.windowForSeconds(seconds));
    }

    @Test
    @DisplayName("firstN 保序截断，limit 不小于大小时原样返回")
    void firstNKeepsOrder() {
        List<VaultLootLogic.Occurrence> occurrences = List.of(
                new VaultLootLogic.Occurrence(5L, "minecraft:diamond", 2),
                new VaultLootLogic.Occurrence(9L, "minecraft:emerald", 1),
                new VaultLootLogic.Occurrence(12L, "minecraft:heavy_core", 1));
        assertEquals(List.of(occurrences.get(0), occurrences.get(1)),
                VaultLootLogic.firstN(occurrences, 2));
        assertEquals(occurrences, VaultLootLogic.firstN(occurrences, 3));
        assertSame(occurrences, VaultLootLogic.firstN(occurrences, 10));
        assertTrue(VaultLootLogic.firstN(occurrences, 0).isEmpty());
        assertTrue(VaultLootLogic.firstN(List.of(), 5).isEmpty());
    }

    @Test
    @DisplayName("occurrences 保持顺序并逐字段映射")
    void occurrencesMapFields() {
        List<VaultLootLogic.Hit> hits = List.of(
                new VaultLootLogic.Hit(1L, "minecraft:enchanted_golden_apple", 1),
                new VaultLootLogic.Hit(7L, "minecraft:heavy_core", 2));
        assertEquals(List.of(
                new VaultLootLogic.Occurrence(1L, "minecraft:enchanted_golden_apple", 1),
                new VaultLootLogic.Occurrence(7L, "minecraft:heavy_core", 2)),
                VaultLootLogic.occurrences(hits));
        assertTrue(VaultLootLogic.occurrences(List.of()).isEmpty());
    }
}
