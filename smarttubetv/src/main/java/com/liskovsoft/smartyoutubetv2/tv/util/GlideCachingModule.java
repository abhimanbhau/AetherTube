package com.liskovsoft.smartyoutubetv2.tv.util;

import android.app.ActivityManager;
import android.content.Context;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator;
import com.bumptech.glide.module.AppGlideModule;

/**
 * https://bumptech.github.io/glide/doc/configuration.html#disk-cache
 */
@GlideModule
public class GlideCachingModule extends AppGlideModule {
    // 10 MB was far too small for a TV browsing grids of thumbnails: a single screenful of a dense
    // grid can approach that, so the LRU evicted images almost as fast as it stored them and scrolling
    // back re-downloaded everything. Glide's own default is 250 MB; TVs have ample internal storage
    // and this is by far the cheapest win for perceived scroll performance on weak hardware.
    private final static long CACHE_SIZE = 256 * 1024 * 1024; // 256 MB
    // Cheap boxes are frequently 8 GB in total, where 256 MB of thumbnails is a less reasonable ask.
    private final static long CACHE_SIZE_LOW_END = 96 * 1024 * 1024;

    // Glide sizes its memory cache in "screens' worth of pixels". Its defaults (2 screens of cache,
    // 4 of bitmap pool) are tuned for phones; a TV screenful of thumbnails is much larger, and a grid
    // gets scrolled back through constantly, so a capable device benefits from holding more. The
    // multiplier is a fraction of the app's available heap, so this can never exceed what the device
    // actually has - a device with a small memoryClass stays small even with a generous screen count.
    private final static float MEMORY_CACHE_SCREENS = 3f;
    private final static float BITMAP_POOL_SCREENS = 3f;
    private final static float MAX_HEAP_FRACTION = 0.45f;

    private final static float MEMORY_CACHE_SCREENS_LOW_END = 1.5f;
    private final static float BITMAP_POOL_SCREENS_LOW_END = 1.5f;
    private final static float MAX_HEAP_FRACTION_LOW_END = 0.25f;

    @Override
    public void applyOptions(Context context, GlideBuilder builder) {
        //if (MyApplication.from(context).isTest())
        //    return; // NOTE: StatFs will crash on robolectric.

        boolean lowEnd = isLowEndDevice(context);

        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, lowEnd ? CACHE_SIZE_LOW_END : CACHE_SIZE));

        MemorySizeCalculator calculator = new MemorySizeCalculator.Builder(context)
                .setMemoryCacheScreens(lowEnd ? MEMORY_CACHE_SCREENS_LOW_END : MEMORY_CACHE_SCREENS)
                .setBitmapPoolScreens(lowEnd ? BITMAP_POOL_SCREENS_LOW_END : BITMAP_POOL_SCREENS)
                .setMaxSizeMultiplier(lowEnd ? MAX_HEAP_FRACTION_LOW_END : MAX_HEAP_FRACTION)
                .build();

        builder.setMemoryCache(new LruResourceCache(calculator.getMemoryCacheSize()));
        builder.setBitmapPool(new LruBitmapPool(calculator.getBitmapPoolSize()));
    }

    /**
     * Mirrors com.abhimankolte.aethertube.tv.ui.common.compose.DevicePerformance. Duplicated rather
     * than shared on purpose: Glide instantiates this module very early in process startup, before
     * the Compose layer exists, so the check has to stay dependency-free.
     */
    private static boolean isLowEndDevice(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        if (am == null) {
            return false;
        }

        return am.isLowRamDevice()
                || am.getMemoryClass() <= 128
                || Runtime.getRuntime().availableProcessors() <= 2;
    }
}
