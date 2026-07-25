package com.kbul.spicycrab.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class SampleSizeTest {

    @Test
    fun smallImagesAreNotDownsampled() {
        assertEquals(1, sample(800, 600))
        assertEquals(1, sample(1024, 768))
        assertEquals(1, sample(2047, 1000))
    }

    @Test
    fun downsamplesToThePowerOfTwoThatKeepsTheLongEdgeAtOrAboveTheTarget() {
        assertEquals(2, sample(2048, 1536))
        assertEquals(4, sample(4096, 3072))
        assertEquals(4, sample(8000, 6000))
        assertEquals(8, sample(8192, 6144))
    }

    @Test
    fun orientationDoesNotMatter() {
        assertEquals(sample(4096, 3072), sample(3072, 4096))
    }

    @Test
    fun undecodableBoundsFallBackToFullDecode() {
        assertEquals(1, sample(-1, -1))
    }

    private fun sample(width: Int, height: Int) =
        ImageUtils.sampleSizeFor(width, height, maxLongEdge = 1024)
}
