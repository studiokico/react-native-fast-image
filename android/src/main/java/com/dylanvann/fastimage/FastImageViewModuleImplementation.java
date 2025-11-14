package com.dylanvann.fastimage;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.views.imagehelper.ImageSource;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReactApplicationContext;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

class FastImageViewModuleImplementation {
    ReactApplicationContext reactContext;
    FastImageViewModuleImplementation(ReactApplicationContext reactContext){

    this.reactContext = reactContext;
    }

    public static final String REACT_CLASS = "FastImageViewModule";

    private Activity getCurrentActivity(){
        return reactContext.getCurrentActivity();
    }

    public void preload(final ReadableArray sources) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);
                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);
                    if (source == null || !source.hasKey("uri") || source.getString("uri").isEmpty()) {
                            System.out.println("Source is null or URI is empty");
                            continue;
                          }
                    Glide
                            .with(activity.getApplicationContext())
                            // This will make this work for remote and local images. e.g.
                            //    - file:///
                            //    - content://
                            //    - res:/
                            //    - android.resource://
                            //    - data:image/png;base64
                            .load(
                                    imageSource.isBase64Resource() ? imageSource.getSource() :
                                    imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl()
                            )
                            .apply(FastImageViewConverter.getOptions(activity, imageSource, source, null))
                            .preload();
                }
            }
        });
    }

    public void preloadAwait(final ReadableArray sources, final Promise promise) {
       // on resolve,
       // returns PreloadAwaitResult
       // export type PreloadAwaitResult = { finished: number; skipped: number };
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(Arguments.createMap());
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int finished = 0;
                int skipped = 0;
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);
                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);
                    if (source == null || !source.hasKey("uri") || source.getString("uri").isEmpty()) {
                        skipped++;
                        continue;
                    }
                    Glide
                            .with(activity.getApplicationContext())
                            .load(
                                    imageSource.isBase64Resource() ? imageSource.getSource() :
                                    imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl()
                            )
                            .apply(FastImageViewConverter.getOptions(activity, imageSource, source))
                            .preload();
                    finished++;
                }
                WritableMap result = Arguments.createMap();
                result.putInt("finished", finished);
                result.putInt("skipped", skipped);
                promise.resolve(result);
            }
        });
    }

    public void queryCache(final ReadableArray urls, final Promise promise) {
        // on resolve,
        // returns QueryCacheResult
        // Record<String, "cached">;

        final Activity activity = getCurrentActivity();
        final WritableMap result = Arguments.createMap();
        if (activity == null) {
            promise.resolve(result);
            return;
        }

        int toCheck = (int) IntStream.range(0, urls.size()).mapToObj(urls::getString).filter(url -> url != null && !url.isEmpty()).count();
        if (toCheck == 0) {
            promise.resolve(result);
            return;
        }

        final AtomicInteger done = new AtomicInteger(0);
        final AtomicBoolean resolved = new AtomicBoolean(false);

        final RequestOptions cacheOnly = new RequestOptions().onlyRetrieveFromCache(true);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < urls.size(); i++) {
                    final String url = urls.getString(i);
                    if (url == null || url.isEmpty()) {
                        if (done.incrementAndGet() == toCheck && resolved.compareAndSet(false, true)) {
                            promise.resolve(result);
                        }
                        continue;
                    }

                    final GlideUrl glideUrl = new GlideUrl(url);

                    Glide.with(activity.getApplicationContext())
                            .downloadOnly()
                            .load(glideUrl)
                            .apply(cacheOnly)
                            .addListener(new RequestListener<>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<File> target, boolean isFirstResource) {
                                    if (done.incrementAndGet() == toCheck && resolved.compareAndSet(false, true)) {
                                        promise.resolve(result);
                                    }
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(@NonNull File resource, @NonNull Object model, Target<File> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                                    result.putString(url, "cached");
                                    if (done.incrementAndGet() == toCheck && resolved.compareAndSet(false, true)) {
                                        promise.resolve(result);
                                    }
                                    return false;
                                }
                            })
                            .preload();
                }
            }
        });
    }

    public void clearMemoryCache(final Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Glide.get(activity.getApplicationContext()).clearMemory();
                promise.resolve(null);
            }
        });
    }

    public void clearDiskCache(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        Glide.get(activity.getApplicationContext()).clearDiskCache();
        promise.resolve(null);
    }
}
