package com.otaliastudios.transcoder.integration

import android.media.MediaFormat
import android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.resize.AspectRatioResizer
import com.otaliastudios.transcoder.source.AssetFileDescriptorDataSource
import com.otaliastudios.transcoder.source.BlankAudioDataSource
import com.otaliastudios.transcoder.source.ClipDataSource
import com.otaliastudios.transcoder.source.FileDescriptorDataSource
import com.otaliastudios.transcoder.source.FilePathDataSource
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.validator.WriteAlwaysValidator
import org.junit.Assume
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class IssuesTests {

    class Helper(val issue: Int) {

        val log = Logger("Issue$issue")
        val context = InstrumentationRegistry.getInstrumentation().context

        fun output(
            name: String = System.currentTimeMillis().toString(),
            extension: String = "mp4"
        ) = File(context.cacheDir, "$name.$extension").also { it.parentFile!!.mkdirs() }

        fun input(filename: String) = AssetFileDescriptorDataSource(
            context.assets.openFd("issue_$issue/$filename")
        )

        fun transcode(
            output: File = output(),
            assertTranscoded: Boolean = true,
            assertDuration: Boolean = true,
            builder: TranscoderOptions.Builder.() -> Unit,
        ): File = runCatching {
            Logger.setLogLevel(Logger.LEVEL_VERBOSE)
            val transcoder = Transcoder.into(output.absolutePath)
            transcoder.apply(builder)
            transcoder.setListener(object : TranscoderListener {
                override fun onTranscodeCanceled() = Unit
                override fun onTranscodeCompleted(successCode: Int) {
                    if (assertTranscoded) {
                        require(successCode == Transcoder.SUCCESS_TRANSCODED)
                    }
                }
                override fun onTranscodeFailed(exception: Throwable) = Unit
                override fun onTranscodeProgress(progress: Double) = Unit
            })
            transcoder.transcode().get()
            if (assertDuration) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(output.absolutePath)
                val duration = retriever.extractMetadata(METADATA_KEY_DURATION)!!.toLong()
                log.e("Video duration is $duration")
                assert(duration > 0L)
                retriever.release()
            }
            return output
        }.getOrElse {
            if (it.toString().contains("c2.android.avc.encoder was unable to create the input surface (1x1)")) {
                log.w("Hit known emulator bug. Skipping the test.")
                throw AssumptionViolatedException("Hit known emulator bug.")
            }
            throw it
        }
        fun getRotation(file:File) : Int {
            val outputDataSource = FilePathDataSource(file.absolutePath)
            var displayRotation = 0
            try{
                outputDataSource.initialize()
                val mediaFormat = outputDataSource.getTrackFormat(TrackType.VIDEO) ?: throw NullPointerException("MediaFormat is null")
                displayRotation = mediaFormat.getInteger(MediaFormat.KEY_ROTATION)
            }catch (_:Exception){

            }finally {
                outputDataSource.deinitialize()
            }
            return displayRotation
        }
        fun getRotation(fd:AssetFileDescriptorDataSource) : Int {
            var displayRotation = 0
            try{
                fd.initialize()
                val mediaFormat = fd.getTrackFormat(TrackType.VIDEO) ?: throw NullPointerException("MediaFormat is null")
                displayRotation = mediaFormat.getInteger(MediaFormat.KEY_ROTATION)
            }catch (_:Exception){

            }finally {
                fd.deinitialize()
            }
            return displayRotation
        }
        fun getFrameSize(fd:AssetFileDescriptorDataSource): Pair<Int, Int>{
            var outputWidth = 0
            var outputHeight = 0
            try{
                fd.initialize()
                val mediaFormat = fd.getTrackFormat(TrackType.VIDEO) ?: throw NullPointerException("MediaFormat is null")
                outputWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                outputHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
            }catch (ex:Exception){
                throw Exception("Get video size\n${ex.localizedMessage}")
            }finally {
                fd.deinitialize()
            }
            return Pair(outputWidth, outputHeight)
        }
        fun getFrameSize(file:File) : Pair<Int, Int> {
            val outputDataSource = FilePathDataSource(file.absolutePath)
            var outputWidth = 0
            var outputHeight = 0
            try{
                outputDataSource.initialize()
                val mediaFormat = outputDataSource.getTrackFormat(TrackType.VIDEO) ?: throw NullPointerException("MediaFormat is null")
                outputWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                outputHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
            }catch (ex:Exception){
                throw Exception("Get video size\n${ex.localizedMessage}")
            }finally {
                outputDataSource.deinitialize()
            }
            return Pair(outputWidth, outputHeight)
        }
    }


    @Test(timeout = 16000)
    fun issue137() = with(Helper(137)) {
        transcode {
            addDataSource(ClipDataSource(input("main.mp3"), 0L, 1000_000L))
            addDataSource(input("0.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 2000_000L, 3000_000L))
            addDataSource(input("1.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 4000_000L, 5000_000L))
            addDataSource(input("2.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 6000_000L, 7000_000L))
            addDataSource(input("3.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 8000_000L, 9000_000L))
            addDataSource(input("4.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 10000_000L, 11000_000L))
            addDataSource(input("5.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 12000_000L, 13000_000L))
            addDataSource(input("6.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 14000_000L, 15000_000L))
            addDataSource(input("7.amr"))
            addDataSource(ClipDataSource(input("main.mp3"), 16000_000L, 17000_000L))
            addDataSource(input("8.amr"))
        }
        Unit
    }

    @Test(timeout = 16000)
    fun issue184() = with(Helper(184)) {
        transcode {
            addDataSource(TrackType.VIDEO, input("transcode.3gp"))
            setVideoTrackStrategy(DefaultVideoStrategy.exact(400, 400).build())
        }
        Unit
    }

    @Test(timeout = 16000)
    fun issue102() = with(Helper(102)) {
        transcode {
            addDataSource(input("sample.mp4"))
            setValidator(WriteAlwaysValidator())
        }
        Unit
    }

    @Test(timeout = 16000)
    fun issue180() = with(Helper(180)) {
        transcode {
            val vds = input("party.mp4")
            val duration = run {
                vds.initialize()
                vds.durationUs.also { vds.deinitialize() }
            }
            check(duration > 0L) { "Invalid duration: $duration" }
            addDataSource(TrackType.VIDEO, vds)
            addDataSource(TrackType.AUDIO, BlankAudioDataSource(duration))
        }
        Unit
    }

    @Test(timeout = 16000)
    fun issue75_workaround() = with(Helper(75)) {
        transcode {
            val vds = input("bbb_720p_30mb.mp4")
            addDataSource(ClipDataSource(vds, 0, 500_000))
            setVideoTrackStrategy(DefaultVideoStrategy.exact(300, 300).build())
            // API 23:
            // This video seems to have wrong number of channels in metadata (6) wrt MediaCodec decoder output (2)
            // so when using DefaultAudioStrategy.CHANNELS_AS_INPUT we target 6 and try to upscale from 2 to 6, which fails
            // The workaround below explicitly sets a number of channels different than CHANNELS_AS_INPUT to make it work
            // setAudioTrackStrategy(DefaultAudioStrategy.builder().channels(1).build())
        }
        Unit
    }
    @Test
    fun issue215() = with(Helper(215)){
        //Display rotation 0, 90, 180, 270
        val rotations = listOf(0,270,180,90)
        val videoNames = listOf("bbb_720p_1sec.mp4", "bbb_720p_1sec_r90.mp4", "bbb_720p_1sec_r180.mp4", "bbb_720p_1sec_r270.mp4")
        videoNames.forEachIndexed { i, videoName ->
            val inputVideoSize = getFrameSize(input(videoName))
            val inputRatio = inputVideoSize.first.toFloat() / inputVideoSize.second.toFloat()
            check(inputRatio > 1f){"Input video ($videoName) ratio is: $inputRatio but expected: greater then 1"}
            val inputRotation = getRotation(input(videoName))
            check(inputRotation == rotations[i]) {"Input video ($videoName) display rotation is: $inputRotation but expected: ${rotations[i]}"}
            val expectedOutputRatio = 0.5f
            val outputVideo = transcode {
                val vds = input(videoName)
                addDataSource(vds)
                val videoTrackStrategy = DefaultVideoStrategy.Builder()
                videoTrackStrategy.addResizer(AspectRatioResizer(expectedOutputRatio))
                setVideoTrackStrategy(videoTrackStrategy.build())
            }
            val outputRotation = getRotation(outputVideo)
            val outputVideoSize = getFrameSize(outputVideo)
            val outputRatio = ((outputVideoSize.first.toFloat() / outputVideoSize.second.toFloat()) * 10).roundToInt() / 10f
            check(outputRatio == expectedOutputRatio) {"Output ratio is: $outputRatio but expects: $expectedOutputRatio (input video[$i]: $videoName)"}
            check(outputRotation == 0) {"Output video display rotation is: $outputRotation but expected: 0 (input video[$i]: $videoName)"}
            println("$videoName (rotation: $outputRotation expected: 0), inputRatio: $inputRatio (${inputVideoSize.first}x${inputVideoSize.second}) expectedOutputRatio: $expectedOutputRatio (${outputVideoSize.first}x${outputVideoSize.second}) OK")
        }
    }
}