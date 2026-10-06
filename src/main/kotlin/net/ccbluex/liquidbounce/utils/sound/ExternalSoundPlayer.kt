package net.ccbluex.liquidbounce.utils.sound

import fr.delthas.javamp3.Sound
import org.lwjgl.openal.AL10
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

object ExternalSoundPlayer {

    private class CachedSound(
        val direct: ByteBuffer,
        val pcm: ShortBuffer,
        val sampleRate: Int,
        val channels: Int
    )

    private val cache = HashMap<String, CachedSound>()
    private val playingSources = HashMap<Int, Int>()

    fun play(resourcePath: String, volume: Float, pitch: Float) {
        try {
            if (!resourcePath.startsWith("/")) return

            val sound = cache.getOrPut(resourcePath) { decode(resourcePath) ?: return }
            if (sound.pcm.capacity() <= 0) return

            sound.pcm.rewind()

            val format = if (sound.channels == 1) AL10.AL_FORMAT_MONO16
            else AL10.AL_FORMAT_STEREO16

            val bufferId = AL10.alGenBuffers()
            AL10.alBufferData(bufferId, format, sound.pcm, sound.sampleRate)

            val err = AL10.alGetError()
            if (err != AL10.AL_NO_ERROR) {
                System.err.println("[KillAuraSound] alBufferData error: 0x${Integer.toHexString(err)}")
            }

            val sourceId = AL10.alGenSources()
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId)
            AL10.alSourcef(sourceId, AL10.AL_GAIN, volume.coerceIn(0f, 1f))
            AL10.alSourcef(sourceId, AL10.AL_PITCH, pitch.coerceIn(0.5f, 2f))
            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
            AL10.alSource3f(sourceId, AL10.AL_POSITION, 0f, 0f, 0f)

            AL10.alSourcePlay(sourceId)

            val state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE)
            System.err.println(
                "[KillAuraSound] play $resourcePath -> ${sound.sampleRate}Hz ${sound.channels}ch, state=$state"
            )

            playingSources[sourceId] = bufferId
        } catch (t: Throwable) {
            System.err.println("[KillAuraSound] play failed: ${t.message}")
        }
    }

    fun tick() {
        try {
            val iter = playingSources.entries.iterator()
            while (iter.hasNext()) {
                val (src, buf) = iter.next()
                if (AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    AL10.alDeleteSources(src)
                    AL10.alDeleteBuffers(buf)
                    iter.remove()
                }
            }
        } catch (_: Throwable) {}
    }

    fun clearCache() = cache.clear()

    private fun decode(resourcePath: String): CachedSound? {
        return try {
            val raw = ExternalSoundPlayer::class.java.getResourceAsStream(resourcePath)
            if (raw == null) {
                System.err.println("[KillAuraSound] resource not found: $resourcePath")
                return null
            }

            //先把文件全读进内存，方便手动定位第一个音频帧
            val fileBytes = raw.use { it.readBytes() }
            if (fileBytes.size < 4) {
                System.err.println("[KillAuraSound] file too small: ${fileBytes.size}B")
                return null
            }

            val start = findAudioStart(fileBytes)
            if (start < 0) {
                System.err.println("[KillAuraSound] no MP3 frame found in $resourcePath")
                return null
            }
            System.err.println("[KillAuraSound] $resourcePath: ${fileBytes.size}B, audio starts at $start")

            val stream = BufferedInputStream(
                ByteArrayInputStream(fileBytes, start, fileBytes.size - start),
                64 * 1024
            )

            Sound(stream).use { sound ->
                val out = ByteArrayOutputStream(512 * 1024)
                val buf = ByteArray(16 * 1024)

                while (true) {
                    val n = try {
                        sound.read(buf)
                    } catch (t: Throwable) {
                        System.err.println("[KillAuraSound] read stopped: ${t.message}")
                        break
                    }
                    if (n < 0) break
                    if (n > 0) out.write(buf, 0, n)
                }

                val pcmBytes = out.toByteArray()
                if (pcmBytes.isEmpty()) {
                    System.err.println("[KillAuraSound] decoded empty: $resourcePath")
                    return null
                }

                val direct = ByteBuffer.allocateDirect(pcmBytes.size)
                    .order(ByteOrder.nativeOrder())
                direct.put(pcmBytes)
                direct.flip()
                val pcm = direct.asShortBuffer()

                val fmt = sound.audioFormat
                val ch = fmt.channels
                val sr = fmt.sampleRate.toInt()
                val sec = pcmBytes.size.toDouble() / (sr * ch * 2)

                System.err.println(
                    "[KillAuraSound] decoded $resourcePath: " +
                        "${pcmBytes.size}B, ${sr}Hz, ${ch}ch, %.3fs".format(sec)
                )

                CachedSound(direct, pcm, sr, ch)
            }
        } catch (t: Throwable) {
            System.err.println("[KillAuraSound] decode failed for $resourcePath: ${t.message}")
            t.printStackTrace()
            null
        }
    }


  // 在字节数组里找第一个真正的音频帧起始偏移。
    // 会跳过 ID3v2 标签和 Xing/Info 头帧。

    private fun findAudioStart(data: ByteArray): Int {
        var i = 0

        //跳过ID3v2
        if (data.size >= 10 &&
            data[0] == 'I'.code.toByte() &&
            data[1] == 'D'.code.toByte() &&
            data[2] == '3'.code.toByte()) {
            val tagSize = ((data[6].toInt() and 0x7F) shl 21) or
                ((data[7].toInt() and 0x7F) shl 14) or
                ((data[8].toInt() and 0x7F) shl 7)  or
                (data[9].toInt() and 0x7F)
            val tagEnd = tagSize + 10
            if (tagEnd in 10 until data.size - 4) {
                i = tagEnd
                System.err.println("[KillAuraSound] skip ID3v2: $tagSize bytes")
            }
        }

        //扫描第一个同步字
        while (i + 4 <= data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = data[i + 1].toInt() and 0xFF

            if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) {
                i++
                continue
            }

            val b2 = data[i + 2].toInt() and 0xFF
            val b3 = data[i + 3].toInt() and 0xFF
            val mpegVer = (b1 shr 3) and 0x03   // 0=2.5, 1=res, 2=MPEG2, 3=MPEG1
            val layer   = (b1 shr 1) and 0x03   // 0=res, 1=L3, 2=L2, 3=L1
            val bitrate = (b2 shr 4) and 0x0F   // 0=free, 15=bad
            val sampler = (b2 shr 2) and 0x03   // 3=reserved
            val chanMod = (b3 shr 6) and 0x03   // 3=mono

            //校验字段合法性
            if (mpegVer == 1 || layer == 0 || bitrate == 0 || bitrate == 15 || sampler == 3) {
                i++
                continue
            }

            //Check Xing/Info 元数据帧
            val sideInfo = when {
                mpegVer == 3 -> if (chanMod == 3) 17 else 32  // MPEG-1
                else         -> if (chanMod == 3) 9  else 17  // MPEG-2 / 2.5
            }
            val xingPos = i + 4 + sideInfo
            val hasXing = xingPos + 4 <= data.size && (
                (data[xingPos] == 'X'.code.toByte() &&
                    data[xingPos + 1] == 'i'.code.toByte() &&
                    data[xingPos + 2] == 'n'.code.toByte() &&
                    data[xingPos + 3] == 'g'.code.toByte()) ||
                    (data[xingPos] == 'I'.code.toByte() &&
                        data[xingPos + 1] == 'n'.code.toByte() &&
                        data[xingPos + 2] == 'f'.code.toByte() &&
                        data[xingPos + 3] == 'o'.code.toByte())
                )

            if (hasXing) {
                val len = calcFrameLength(mpegVer, layer, bitrate, sampler, (b2 shr 1) and 0x01)
                if (len > 4 && i + len <= data.size) {
                    System.err.println("[KillAuraSound] skip Xing/Info frame at $i, len=$len")
                    i += len
                    continue
                } else {
                    // 算不出帧长，保守跳过 4 字节继续找
                    i += 4
                    continue
                }
            }

            return i
        }

        return -1
    }

   //处理Mp3
    private fun calcFrameLength(
        mpegVer: Int,
        layer: Int,
        bitrateIdx: Int,
        sampleRateIdx: Int,
        padding: Int
    ): Int {
        if (mpegVer != 3 || layer != 1) return 0
        val bitrateTable = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        val sampleRateTable = intArrayOf(44100, 48000, 32000, 0)
        val br = bitrateTable[bitrateIdx] * 1000
        val sr = sampleRateTable[sampleRateIdx]
        if (br == 0 || sr == 0) return 0
        return 144 * br / sr + padding
    }
}
