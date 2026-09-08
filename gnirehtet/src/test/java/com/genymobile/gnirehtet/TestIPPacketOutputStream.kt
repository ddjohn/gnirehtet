/*
 * Copyright (C) 2017 Genymobile
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.genymobile.gnirehtet

import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class TestIPPacketOutputSteam {
    private fun createMockPacket(): ByteBuffer {
        val buffer = ByteBuffer.allocate(32)
        writeMockPacketTo(buffer)
        buffer.flip()
        return buffer
    }

    private fun writeMockPacketTo(buffer: ByteBuffer) {
        buffer.put(((4 shl 4) or 5).toByte()) // versionAndIHL
        buffer.put(0.toByte()) // ToS
        buffer.putShort(32.toShort()) // total length 20 + 8 + 4
        buffer.putInt(0) // IdFlagsFragmentOffset
        buffer.put(0.toByte()) // TTL
        buffer.put(17.toByte()) // protocol (UDP)
        buffer.putShort(0.toShort()) // checksum
        buffer.putInt(0x12345678) // source address
        buffer.putInt(0x42424242) // destination address

        buffer.putShort(1234.toShort()) // source port
        buffer.putShort(5678.toShort()) // destination port
        buffer.putShort(12.toShort()) // length
        buffer.putShort(0.toShort()) // checksum

        buffer.putInt(0x11223344) // payload
    }

    @Test
    @Throws(IOException::class)
    fun testSimplePacket() {
        val bos = ByteArrayOutputStream()
        val pos: IPPacketOutputStream = IPPacketOutputStream(bos)

        val rawPacket = createMockPacket().array()

        pos.write(rawPacket, 0, 14)
        Assert.assertEquals("Partial packet should not be written", 0, bos.size().toLong())

        pos.write(rawPacket, 14, 14)
        Assert.assertEquals("Partial packet should not be written", 0, bos.size().toLong())

        pos.write(rawPacket, 28, 4)
        Assert.assertEquals("Complete packet should be written", 32, bos.size().toLong())

        val result = bos.toByteArray()
        Assert.assertTrue("Resulting array must be identical", rawPacket.contentEquals(result))
    }

    @Test
    @Throws(IOException::class)
    fun testSeveralPacketsAtOnce() {
        class CapturingOutputStream : ByteArrayOutputStream() {
            var packetCount = 0

            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                ++packetCount
            }
        }

        val cos = CapturingOutputStream()
        val pos: IPPacketOutputStream = IPPacketOutputStream(cos)

        val buffer = ByteBuffer.allocate(3 * 32)
        for (i in 0..2) {
            writeMockPacketTo(buffer)
        }
        val rawPackets = buffer.array()

        pos.write(rawPackets, 0, 70) // 2 packets + 6 bytes
        Assert.assertEquals("Exactly 2 packets should have been written", 64, cos.size().toLong())
        Assert.assertEquals(
            "Packets should be written individually to the target",
            2,
            cos.packetCount.toLong()
        )

        pos.write(rawPackets, 70, 26)
        Assert.assertEquals("Exactly 3 packets should have been written", 96, cos.size().toLong())
        Assert.assertEquals(
            "Packets should be written individually to the target",
            3,
            cos.packetCount.toLong()
        )
    }
}