package com.xartifex.fiddles.ignite

import org.apache.ignite.IgniteCache
import org.apache.ignite.Ignition
import org.apache.ignite.cluster.ClusterState
import java.sql.Timestamp


object DataSizeEstimation {
    private val config: String
        get() = javaClass.classLoader.getResource("config-data-size-estimation.xml")!!.toString()

    private fun createSampleValue(i: Int): Value {
        return Value(i, "123456789$i", Timestamp(System.currentTimeMillis()))
    }


    @JvmStatic
    fun main(args: Array<String>) {
        Ignition.start(config).use { ignite ->
            ignite.cluster().state(ClusterState.ACTIVE)
            println("Populating the cache...")
            val cache: IgniteCache<Long, Value> = ignite.cache("myCache")
            for (i in 0..2000000) {
                cache.put(i.toLong(), createSampleValue(i))
            }
            println("Total storage size: " + ignite.dataStorageMetrics().totalAllocatedSize)
            ignite.cluster().state(ClusterState.INACTIVE)
        }
    }

    internal class Value(var id: Int, var name: String, var date: Timestamp)
}