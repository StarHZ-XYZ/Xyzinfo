package com.rjy.xyz.apps.xyzinfo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 电池节点单位回归：能量节点必须按电压换算成电荷容量。 */
class BatteryCapacityParserTest {

    @Test
    fun `微瓦时能量节点按电压换算`() {
        assertEquals(
            5_000,
            BatteryCapacityParser.toMah(19_000_000, CapacitySourceKind.ENERGY, 3_800)
        )
    }

    @Test
    fun `毫瓦时能量节点按电压换算`() {
        assertEquals(
            5_000,
            BatteryCapacityParser.toMah(19_000, CapacitySourceKind.ENERGY, 3_800)
        )
    }

    @Test
    fun `微安时和毫安时电荷节点直接换算`() {
        assertEquals(
            5_000,
            BatteryCapacityParser.toMah(5_000_000, CapacitySourceKind.CHARGE, 3_800)
        )
        assertEquals(
            5_000,
            BatteryCapacityParser.toMah(5_000, CapacitySourceKind.CHARGE, 3_800)
        )
    }

    @Test
    fun `容量明显不合理的节点不接受`() {
        assertNull(BatteryCapacityParser.toMah(0, CapacitySourceKind.CHARGE, 3_800))
        assertNull(BatteryCapacityParser.toMah(500_000_000, CapacitySourceKind.CHARGE, 3_800))
    }
}
