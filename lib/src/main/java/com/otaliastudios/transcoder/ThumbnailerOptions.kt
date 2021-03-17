package com.otaliastudios.transcoder

import com.otaliastudios.transcoder.source.DataSource

class ThumbnailerOptions {

    class Builder {

        private val dataSources = mutableListOf<DataSource>()

        fun addDataSource()

        fun build() = ThumbnailerOptions()
    }
}