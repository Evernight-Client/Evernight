package evernight.client.utils.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VaultComponentHashTest {
    @Test
    @DisplayName("0 保留给“无组件”，finish 把它映射为 1")
    void zeroIsReservedForNoComponents() {
        assertEquals(1L, VaultComponentHash.finish(0L));
        assertEquals(1L, VaultComponentHash.finish(1L));
        assertEquals(5L, VaultComponentHash.finish(5L));
        assertEquals(-3L, VaultComponentHash.finish(-3L));
    }

    @Test
    @DisplayName("附魔/药水/不祥增幅/伤害四类指纹互不相同")
    void componentCategoriesAreDistinct() {
        long enchantment = VaultComponentHash.enchantment(1, 2);
        long potion = VaultComponentHash.potion(1);
        long amplifier = VaultComponentHash.ominousAmplifier(1);
        long damage = VaultComponentHash.damage(1);
        assertNotEquals(enchantment, potion);
        assertNotEquals(enchantment, amplifier);
        assertNotEquals(enchantment, damage);
        assertNotEquals(potion, amplifier);
        assertNotEquals(potion, damage);
        assertNotEquals(amplifier, damage);
        for (long hash : new long[] {enchantment, potion, amplifier, damage}) {
            assertNotEquals(0L, hash);
            assertNotEquals(0L, VaultComponentHash.finish(hash));
        }
    }

    @Test
    @DisplayName("不同附魔 id/等级产生不同指纹")
    void differentEnchantmentsDiffer() {
        assertNotEquals(VaultComponentHash.enchantment(1, 1),
                VaultComponentHash.enchantment(2, 1));
        assertNotEquals(VaultComponentHash.enchantment(1, 1),
                VaultComponentHash.enchantment(1, 2));
        assertNotEquals(VaultComponentHash.potion(1), VaultComponentHash.potion(2));
        assertNotEquals(VaultComponentHash.ominousAmplifier(0),
                VaultComponentHash.ominousAmplifier(1));
        assertNotEquals(VaultComponentHash.damage(0), VaultComponentHash.damage(1));
    }

    @Test
    @DisplayName("附魔项按和合并，与顺序无关")
    void enchantmentTermsAreOrderIndependent() {
        long first = VaultComponentHash.enchantment(3, 2);
        long second = VaultComponentHash.enchantment(8, 5);
        assertEquals(VaultComponentHash.finish(first + second),
                VaultComponentHash.finish(second + first));
    }

    @Test
    @DisplayName("mix 对相同输入稳定，对相邻输入不同")
    void mixIsStableAndSensitive() {
        assertEquals(VaultComponentHash.mix(123L), VaultComponentHash.mix(123L));
        assertNotEquals(VaultComponentHash.mix(123L), VaultComponentHash.mix(124L));
        assertNotEquals(VaultComponentHash.mix(0L), VaultComponentHash.mix(1L));
    }
}
