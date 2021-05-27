package com.otaliastudios.transcoder.custom

import android.content.Context
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.resize.PassThroughResizer
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy

class OtaliaExporterNew {

    fun start(context: Context) {
        val builder = Transcoder.into("/sdcard/transcoder.mp4")
        builder.setListener(object : TranscoderListener {
            override fun onTranscodeProgress(progress: Double) {
            }

            override fun onTranscodeCompleted(successCode: Int) {
            }

            override fun onTranscodeCanceled() {
            }

            override fun onTranscodeFailed(exception: Throwable) {
            }
        })
            .addDataSource("/sdcard/big_bunny.mp4")// necessary to add but not used
            .setAudioTrackStrategy(RemoveTrackStrategy())
            .setVideoTrackStrategy(
                DefaultVideoStrategy.Builder()
                    .addResizer(PassThroughResizer())
                    .frameRate(30)
                    .build()
            )
            .build()
        builder.transcode(context)


    }
}