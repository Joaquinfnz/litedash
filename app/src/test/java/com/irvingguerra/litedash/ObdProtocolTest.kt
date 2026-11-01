package com.irvingguerra.litedash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdProtocolTest {
    @Test fun rpm() = assertEquals(1726f, ObdProtocol.parse(ObdProtocol.Pid.RPM, "410C1AF8\r\r>")!!, 0.01f)

    @Test fun rpmWithSpacesAndSearching() =
        assertEquals(1726f, ObdProtocol.parse(ObdProtocol.Pid.RPM, "SEARCHING...\r41 0C 1A F8 \r\r>")!!, 0.01f)

    @Test fun coolant() = assertEquals(90f, ObdProtocol.parse(ObdProtocol.Pid.COOLANT, "410582\r>")!!, 0.01f)

    @Test fun fuelTrimNegative() = assertEquals(-12.5f, ObdProtocol.parse(ObdProtocol.Pid.LTFT, "410770\r>")!!, 0.01f)

    @Test fun maf() = assertEquals(4.5f, ObdProtocol.parse(ObdProtocol.Pid.MAF, "411001C2\r>")!!, 0.01f)

    @Test fun noData() = assertNull(ObdProtocol.parse(ObdProtocol.Pid.SPEED, "NO DATA\r>"))

    @Test fun voltage() = assertEquals(12.6f, ObdProtocol.parseVoltage("12.6V\r>")!!, 0.01f)

    @Test fun dtcs() = assertEquals(listOf("P0172", "P0300"), ObdProtocol.parseDtcs("43017203000000\r>"))

    @Test fun noDtcs() = assertEquals(emptyList<String>(), ObdProtocol.parseDtcs("43000000000000\r>"))

    @Test fun instantEconomy() {
        // 90 km/h con 5 g/s de aire ≈ 1,64 L/h ≈ 54,8 km/L (crucero liviano).
        assertEquals(54.8f, ObdProtocol.instantKmPerLiter(90f, 5f)!!, 0.2f)
        assertNull(ObdProtocol.instantKmPerLiter(0f, 5f))
    }
}
