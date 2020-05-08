/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package thanatos.android.camera.core;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Surface;

import java.util.concurrent.Executor;

import thanatos.android.camera.core.impl.utils.MainThreadAsyncHandler;
import thanatos.android.camera.core.impl.utils.executor.CameraXExecutors;

/**
 * An {@link ImageReaderProxy} which wraps around an {@link ImageReader}.
 *
 * <p>All methods map one-to-one between this {@link ImageReaderProxy} and the wrapped {@link
 * ImageReader}.
 */
final class AndroidImageReaderProxy implements ImageReaderProxy {
    @GuardedBy("this")
    private final ImageReader mImageReader;

    /**
     * Creates a new instance which wraps the given image reader.
     *
     * @param imageReader to wrap
     * @return new {@link AndroidImageReaderProxy} instance
     */
    AndroidImageReaderProxy(ImageReader imageReader) {
        mImageReader = imageReader;
    }

    @Override
    @Nullable
    public synchronized ImageProxy acquireLatestImage() {
        Image image = mImageReader.acquireLatestImage();
        if (image == null) {
            return null;
        }
        return new AndroidImageProxy(image);
    }

    @Override
    @Nullable
    public synchronized ImageProxy acquireNextImage() {
        Image image = mImageReader.acquireNextImage();
        if (image == null) {
            return null;
        }
        return new AndroidImageProxy(image);
    }

    @Override
    public synchronized void close() {
        mImageReader.close();
    }

    @Override
    public synchronized int getHeight() {
        return mImageReader.getHeight();
    }

    @Override
    public synchronized int getWidth() {
        return mImageReader.getWidth();
    }

    @Override
    public synchronized int getImageFormat() {
        return mImageReader.getImageFormat();
    }

    @Override
    public synchronized int getMaxImages() {
        return mImageReader.getMaxImages();
    }

    @Override
    public synchronized Surface getSurface() {
        return mImageReader.getSurface();
    }

    @Override
    public synchronized void setOnImageAvailableListener(
            @NonNull final OnImageAvailableListener listener,
            @Nullable Handler handler) {
        // Unlike ImageReader.setOnImageAvailableListener(), if handler == null, the callback
        // will not be triggered at all, instead of being triggered on main thread.
        setOnImageAvailableListener(listener,
                handler == null ? null : CameraXExecutors.newHandlerExecutor(handler));
    }

    @Override
    public synchronized void setOnImageAvailableListener(
            @NonNull final OnImageAvailableListener listener,
            @NonNull final Executor executor) {
        // ImageReader does not accept an executor. As a workaround, the callback is run on main
        // handler then immediately posted to the executor.
        ImageReader.OnImageAvailableListener transformedListener =
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                listener.onImageAvailable(AndroidImageReaderProxy.this);
                            }
                        });

                    }
                };
        mImageReader.setOnImageAvailableListener(transformedListener,
                MainThreadAsyncHandler.getInstance());
    }

}
