package com.xartifex.fiddles.ignite

import org.apache.ignite.Ignition
import org.apache.ignite.configuration.IgniteConfiguration
import org.apache.ignite.spi.metric.LongMetric
import org.apache.ignite.spi.metric.Metric
import org.apache.ignite.spi.metric.jmx.JmxMetricExporterSpi
import java.sql.Timestamp
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.stream.Collectors.toSet
import java.util.stream.StreamSupport


object DataSizeEstimation {
    private val config: String
        get() = javaClass.classLoader.getResource("config-data-size-estimation.xml")!!.toString()

    private fun createSampleValue(i: Int): Value {
        return Value(i, "123456789$i", Timestamp(System.currentTimeMillis()))
    }

    const val ENTRIES_NUM = 100
    const val MSG_LENGTH = 1_000_000

    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private fun getRandomString(): String {
        return (1..MSG_LENGTH)
            .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    private fun humanReadableByteCountBin(bytes: Long): String {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(bytes)
        if (absB < 1024) {
            return "$bytes B"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        return String.format("%.1f %ciB", value / 1024.0, ci.current())
    }


    @JvmStatic
    fun main(args: Array<String>) {
        println("--------------" + getRandomString())


        val jmxSpi = JmxMetricExporterSpi()

        val ignite = Ignition.start(
            IgniteConfiguration()
                .setIgniteInstanceName("jmxExampleInstanceName")
                .setMetricExporterSpi(jmxSpi)
        )

        print("Populating cache...")
        ignite.createCache<Long, String>("myCache")
        ignite.dataStreamer<Long, String>("myCache").use { streamer ->
            for (i in 1..ENTRIES_NUM) {
                streamer.addData(i.toLong(), getRandomString())
            }
        }
        println("done")

        val ioReg = jmxSpi.spiContext.getOrCreateMetricRegistry("io.dataregion.default")

        val listOfMetrics: Set<*> = StreamSupport.stream(ioReg.spliterator(), false)
            .map { obj: Metric -> obj.name() }
            .collect(toSet())

        println("The list of available data region metrics: $listOfMetrics")
        println("The 'default' data region MaxSize: " + ioReg.findMetric<LongMetric>("MaxSize")?.value())
        println("---------------------------------------")
        println(
            "TotalAllocatedSize for map size of $ENTRIES_NUM entries, $MSG_LENGTH bytes each: " + humanReadableByteCountBin(
                ioReg.findMetric<LongMetric>("TotalAllocatedSize")!!.value()
            )
        )
        println("---------------------------------------")
        ignite.cluster().stopNodes()
    }
}

internal class Value(var id: Int, var name: String, var date: Timestamp)
