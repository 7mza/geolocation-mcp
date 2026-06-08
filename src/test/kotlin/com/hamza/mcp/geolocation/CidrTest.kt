package com.hamza.mcp.geolocation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CidrTest {
    @Test
    fun `toIpv4LongOrNull converts valid ip`() {
        assertThat("2.26.157.0".toIpv4LongOrNull()).isEqualTo(35298560L)
        assertThat("2.57.68.0".toIpv4LongOrNull()).isEqualTo(37307392L)
        assertThat("255.255.255.255".toIpv4LongOrNull()).isEqualTo(4294967295L)
        assertThat("0.0.0.0".toIpv4LongOrNull()).isEqualTo(0L)
    }

    @Test
    fun `toIpv4LongOrNull returns null for ipv6`() {
        assertThat("2001:4860:4860::8888".toIpv4LongOrNull()).isNull()
    }

    @Test
    fun `cidrToRange matches subnet to correct ranges`() {
        assertThat("2.26.157.0/24".cidrToRange()).isEqualTo(35298560L to 35298815L)
        assertThat("2.57.68.0/22".cidrToRange()).isEqualTo(37307392L to 37308415L)
        assertThat("2.58.241.68/30".cidrToRange()).isEqualTo(37417284L to 37417287L)
        assertThat("1.116.0.0/15".cidrToRange()).isEqualTo(24379392L to 24510463L)
        assertThat("2.16.64.0/22".cidrToRange()).isEqualTo(34619392L to 34620415L)
        assertThat("152.234.173.0/24".cidrToRange()).isEqualTo(2565516544L to 2565516799L)
    }

    @Test
    fun `containsIp finds ip inside range`() {
        val ranges =
            listOf("2.26.157.0/24", "2.57.68.0/22")
                .map { it.cidrToRange() }
                .sortedBy { it.first }
        assertThat(ranges.containsIp("2.26.157.1".toIpv4LongOrNull()!!)).isTrue()
        assertThat(ranges.containsIp("2.26.157.255".toIpv4LongOrNull()!!)).isTrue()
        assertThat(ranges.containsIp("2.57.71.255".toIpv4LongOrNull()!!)).isTrue()
    }

    @Test
    fun `containsIp rejects ip outside range`() {
        val ranges =
            listOf("2.26.157.0/24", "2.57.68.0/22")
                .map { it.cidrToRange() }
                .sortedBy { it.first }
        assertThat(ranges.containsIp("2.26.156.255".toIpv4LongOrNull()!!)).isFalse()
        assertThat(ranges.containsIp("2.26.158.0".toIpv4LongOrNull()!!)).isFalse()
        assertThat(ranges.containsIp("2.57.72.0".toIpv4LongOrNull()!!)).isFalse()
    }

    @Test
    fun `containsIp on empty list returns false`() {
        assertThat(emptyList<Pair<Long, Long>>().containsIp(35298561L)).isFalse()
    }
}
