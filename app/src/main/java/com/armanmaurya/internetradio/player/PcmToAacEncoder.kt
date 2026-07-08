package com.armanmaurya.internetradio.player

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.FileDescriptor
import java.nio.ByteBuffer

class PcmToAacEncoder(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bytesPerFrame: Int,
    private val fileDescriptor: FileDescriptor?,
    private val filePath: String?
) {
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()
    private var isRecording = false
    private var totalFramesEncoded: Long = 0

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1048576)
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.start()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && fileDescriptor != null) {
                mediaMuxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else if (filePath != null) {
                mediaMuxer = MediaMuxer(filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                throw IllegalArgumentException("Either fileDescriptor (API 26+) or filePath must be provided")
            }

            isRecording = true
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error initializing encoder", e)
            release()
        }
    }

    @Synchronized
    fun encode(pcmData: ByteArray, offset: Int, length: Int) {
        if (!isRecording) return

        try {
            val codec = mediaCodec ?: return
            val inputBufferIndex = codec.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData, offset, length)
                
                val numFrames = length / bytesPerFrame
                val presentationTimeUs = (totalFramesEncoded * 1000000L) / sampleRate
                totalFramesEncoded += numFrames
                codec.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs, 0)
            }

            drain(false)
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error encoding audio", e)
        }
    }

    private fun drain(endOfStream: Boolean) {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return
        var tryAgainCount = 0

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)

            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break
                }
                tryAgainCount++
                if (tryAgainCount > 100) {
                    Log.w("PcmToAacEncoder", "Timeout waiting for EOS")
                    break
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) {
                    throw RuntimeException("Format changed twice")
                }
                val newFormat = codec.outputFormat
                trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                muxerStarted = true
            } else if (outputBufferIndex < 0) {
                // Ignore other statuses
            } else {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size != 0 && muxerStarted) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d("PcmToAacEncoder", "End of stream reached")
                    break
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!isRecording) return
        isRecording = false
        
        try {
            val codec = mediaCodec
            if (codec != null) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val presentationTimeUs = (totalFramesEncoded * 1000000L) / sampleRate
                    codec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drain(true)
            }
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error stopping encoder", e)
        } finally {
            release()
        }
    }

    private fun release() {
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error stopping MediaCodec", e)
        }
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error releasing MediaCodec", e)
        }
        mediaCodec = null

        if (muxerStarted) {
            try {
                mediaMuxer?.stop()
            } catch (e: Exception) {
                Log.e("PcmToAacEncoder", "Error stopping MediaMuxer", e)
            }
        }
        try {
            mediaMuxer?.release()
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error releasing MediaMuxer", e)
        }
        mediaMuxer = null
        muxerStarted = false
    }
}
