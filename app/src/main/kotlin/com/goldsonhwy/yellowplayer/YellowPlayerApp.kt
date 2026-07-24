package com.goldsonhwy.yellowplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.decode.VideoFrameDecoder
import com.goldsonhwy.yellowplayer.data.scanner.LocalGalleryCache
import com.goldsonhwy.yellowplayer.util.CrashReporter

class YellowPlayerApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CrashReporter.writeDebugSnapshot(this, "application start")
        LocalGalleryCache.start(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(128 * 1024 * 1024)
                    .build()
            }
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
