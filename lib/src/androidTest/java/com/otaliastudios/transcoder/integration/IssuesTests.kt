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
import com.otaliastudios.transcoder.source.AssetFileDescriptorDataSource
import com.otaliastudios.transcoder.source.ClipDataSource
import com.otaliastudios.transcoder.source.FileDescriptorDataSource
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.validator.WriteAlwaysValidator
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

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
        ): File {
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
        }
    }


    @Test(timeout = 5000)
    fun issue137() = with(Helper(137)) {
        transcode {
            // addDataSource(ClipDataSource(input("main.mp3"), 0L, 200_000L))

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

    @Test(timeout = 5000)
    fun issue184() = with(Helper(184)) {
        transcode {
            addDataSource(TrackType.VIDEO, input("transcode.3gp"))
            setVideoTrackStrategy(DefaultVideoStrategy.exact(400, 400).build())
        }
        Unit
    }

    @Test(timeout = 5000)
    fun issue102() = with(Helper(102)) {
        transcode {
            addDataSource(input("sample.mp4"))
            setValidator(WriteAlwaysValidator())
        }
        Unit
    }
}