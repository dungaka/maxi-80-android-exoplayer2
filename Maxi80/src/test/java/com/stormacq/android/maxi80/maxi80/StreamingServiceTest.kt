package com.stormacq.android.maxi80.maxi80

import org.junit.Test
import org.junit.Assert.*

import com.stormacq.android.maxi80.StreamingService

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class MetaDataUnitTest {

    private fun test(RAW : String) {
        val rawMetaData = "ICY: title=\"$RAW\", url=\"null\""
        val ss = StreamingService()
        val md = ss.parseMetadata(rawMetaData)

        assertEquals(RAW, md.streamTitle)
    }
    @Test
    fun parse_metadata1_test() {
        test("Gianna Nannini - I maschi (N2 du ToP 50 le 24-10-88)")
    }

    @Test
    fun parse_metadata2_test() {
        test("Gianna Nannini - I maschi, the remix")
    }

    @Test
    fun parse_metadata3_test() {
        test("Gianna Nannini-I maschi")
    }

    @Test
    fun parse_metadata4_test() {
        test("\"Gianna Nannini - I maschi\"")
    }}