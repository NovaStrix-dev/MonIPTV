package com.perso.moniptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Chargement des affiches : même User-Agent que le lecteur (certains serveurs IPTV
 * bloquent sinon les images), cache disque de 500 Mo pour un affichage rapide
 * et hors ligne des affiches déjà vues.
 */
class App : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            Http.client.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", Http.USER_AGENT).build())
                }
                .build()
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("posters"))
                .maxSizeBytes(500L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
