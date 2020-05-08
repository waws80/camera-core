/*
 * Copyright 2019 The Android Open Source Project
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

import android.media.ImageReader;
import android.os.Handler;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;
import android.view.Surface;


import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import thanatos.android.camera.core.impl.utils.executor.CameraXExecutors;
import thanatos.android.camera.core.impl.utils.futures.FutureCallback;
import thanatos.android.camera.core.impl.utils.futures.Futures;

/**
 * An {@link ImageReaderProxy} which takes one or more {@link android.media.Image}, processes it,
 * then output the final result {@link ImageProxy} to
 * {@link OnImageAvailableListener}.
 *
 * <p>ProcessingImageReader takes {@link CaptureBundle} as the expected set of
 * {@link CaptureStage}. Once all the ImageProxy from the captures are ready. It invokes
 * the {@link CaptureProcessor} set, then returns a single output ImageProxy to
 * OnImageAvailableListener.
 */
class ProcessingImageReader implements ImageReaderProxy {
    private static final String TAG = "ProcessingImageReader";
    private final Object mLock = new Object();

    // Callback when Image is ready from InputImageReader.
    private OnImageAvailableListener mTransformedListener =
            new OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReaderProxy reader) {
                    imageIncoming(reader);
                }
            };

    // Callback when Image is ready from OutputImageReader.
    private OnImageAvailableListener mImageProcessedListener =
            new OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReaderProxy reader) {
                    // Callback the output OnImageAvailableListener.
                    if (mExecutor != null) {
                        mExecutor.execute(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        mListener.onImageAvailable(ProcessingImageReader.this);
                                    }
                                });
                    } else {
                        mListener.onImageAvailable(ProcessingImageReader.this);
                    }
                    // Resets SettableImageProxyBundle after the processor finishes processing.
                    mSettableImageProxyBundle.reset();
                    setupSettableImageProxyBundleCallbacks();
                }
            };

    // Callback when all the ImageProxies in SettableImageProxyBundle are ready.
    private FutureCallback<List<ImageProxy>> mCaptureStageReadyCallback =
            new FutureCallback<List<ImageProxy>>() {
                @Override
                public void onSuccess(@Nullable List<ImageProxy> imageProxyList) {
                    mCaptureProcessor.process(mSettableImageProxyBundle);
                }

                @Override
                public void onFailure(Throwable throwable) {

                }
            };

    @GuardedBy("mLock")
    private boolean mClosed = false;

    @GuardedBy("mLock")
    private final ImageReaderProxy mInputImageReader;

    @GuardedBy("mLock")
    private final ImageReaderProxy mOutputImageReader;

    @GuardedBy("mLock")
    @Nullable
    OnImageAvailableListener mListener;

    @GuardedBy("mLock")
    @Nullable
    Executor mExecutor;

    @NonNull
    CaptureProcessor mCaptureProcessor;

    @GuardedBy("mLock")
    SettableImageProxyBundle mSettableImageProxyBundle = null;

    private final List<Integer> mCaptureIdList = new ArrayList<>();

    /**
     * Create a {@link ProcessingImageReader} with specific configurations.
     *
     * @param width            Width of the ImageReader
     * @param height           Height of the ImageReader
     * @param format           Image format
     * @param maxImages        Maximum Image number the ImageReader can hold. The capacity should
     *                         be greater than the captureBundle size in order to hold all the
     *                         Images needed with this processing.
     * @param handler          Handler for executing
     *                         {@link OnImageAvailableListener}
     * @param captureBundle    The {@link CaptureBundle} includes the processing information
     * @param captureProcessor The {@link CaptureProcessor} to be invoked when the Images are ready
     */
    ProcessingImageReader(int width, int height, int format, int maxImages,
                          @Nullable Handler handler,
                          @NonNull CaptureBundle captureBundle, @NonNull CaptureProcessor captureProcessor) {
        mInputImageReader = new MetadataImageReader(
                width,
                height,
                format,
                maxImages,
                handler);
        mOutputImageReader = new AndroidImageReaderProxy(
                ImageReader.newInstance(width, height, format, maxImages));

        init(CameraXExecutors.newHandlerExecutor(handler), captureBundle, captureProcessor);
    }

    ProcessingImageReader(ImageReaderProxy imageReader, @Nullable Handler handler,
                          @NonNull CaptureBundle captureBundle,
                          @NonNull CaptureProcessor captureProcessor) {
        if (imageReader.getMaxImages() < captureBundle.getCaptureStages().size()) {
            throw new IllegalArgumentException(
                    "MetadataImageReader is smaller than CaptureBundle.");
        }
        mInputImageReader = imageReader;
        mOutputImageReader = new AndroidImageReaderProxy(
                ImageReader.newInstance(imageReader.getWidth(),
                        imageReader.getHeight(), imageReader.getImageFormat(),
                        imageReader.getMaxImages()));

        init(CameraXExecutors.newHandlerExecutor(handler), captureBundle, captureProcessor);
    }

    private void init(@NonNull Executor executor, @NonNull CaptureBundle captureBundle,
            @NonNull CaptureProcessor captureProcessor) {
        mExecutor = executor;
        mInputImageReader.setOnImageAvailableListener(mTransformedListener, executor);
        mOutputImageReader.setOnImageAvailableListener(mImageProcessedListener, executor);
        mCaptureProcessor = captureProcessor;
        mCaptureProcessor.onOutputSurface(mOutputImageReader.getSurface(), getImageFormat());
        mCaptureProcessor.onResolutionUpdate(
                new Size(mInputImageReader.getWidth(), mInputImageReader.getHeight()));

        setCaptureBundle(captureBundle);
    }

    @Override
    @Nullable
    public ImageProxy acquireLatestImage() {
        synchronized (mLock) {
            return mOutputImageReader.acquireLatestImage();
        }
    }

    @Override
    @Nullable
    public ImageProxy acquireNextImage() {
        synchronized (mLock) {
            return mOutputImageReader.acquireNextImage();
        }
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }

            mInputImageReader.close();
            mOutputImageReader.close();
            mSettableImageProxyBundle.close();
            mClosed = true;
        }
    }

    @Override
    public int getHeight() {
        synchronized (mLock) {
            return mInputImageReader.getHeight();
        }
    }

    @Override
    public int getWidth() {
        synchronized (mLock) {
            return mInputImageReader.getWidth();
        }
    }

    @Override
    public int getImageFormat() {
        synchronized (mLock) {
            return mInputImageReader.getImageFormat();
        }
    }

    @Override
    public int getMaxImages() {
        synchronized (mLock) {
            return mInputImageReader.getMaxImages();
        }
    }

    @Override
    public Surface getSurface() {
        synchronized (mLock) {
            return mInputImageReader.getSurface();
        }
    }

    @Override
    public void setOnImageAvailableListener(
            @NonNull final OnImageAvailableListener listener,
            @Nullable Handler handler) {
        setOnImageAvailableListener(listener, CameraXExecutors.newHandlerExecutor(handler));
    }

    @Override
    public void setOnImageAvailableListener(@NonNull OnImageAvailableListener listener,
            @NonNull Executor executor) {
        // TODO(b/115747543) support callback on executor
        synchronized (mLock) {
            mListener = listener;
            mExecutor = executor;
            mInputImageReader.setOnImageAvailableListener(mTransformedListener, executor);
            mOutputImageReader.setOnImageAvailableListener(mImageProcessedListener, executor);
        }
    }

    /** Sets a CaptureBundle */
    public void setCaptureBundle(@NonNull CaptureBundle captureBundle) {
        synchronized (mLock) {
            if (captureBundle.getCaptureStages() != null) {
                if (mInputImageReader.getMaxImages() < captureBundle.getCaptureStages().size()) {
                    throw new IllegalArgumentException(
                            "CaptureBundle is lager than InputImageReader.");
                }

                mCaptureIdList.clear();

                for (CaptureStage captureStage : captureBundle.getCaptureStages()) {
                    if (captureStage != null) {
                        mCaptureIdList.add(captureStage.getId());
                    }
                }
            }

            mSettableImageProxyBundle = new SettableImageProxyBundle(mCaptureIdList);
            setupSettableImageProxyBundleCallbacks();
        }
    }

    /** Returns necessary camera callbacks to retrieve metadata from camera result. */
    @Nullable
    CameraCaptureCallback getCameraCaptureCallback() {
        if (mInputImageReader instanceof MetadataImageReader) {
            return ((MetadataImageReader) mInputImageReader).getCameraCaptureCallback();
        } else {
            return null;
        }
    }

    void setupSettableImageProxyBundleCallbacks() {
        List<ListenableFuture<ImageProxy>> futureList = new ArrayList<>();
        for (Integer id : mCaptureIdList) {
            futureList.add(mSettableImageProxyBundle.getImageProxy((id)));
        }
        Futures.addCallback(Futures.allAsList(futureList), mCaptureStageReadyCallback,
                CameraXExecutors.directExecutor());
    }

    // Incoming Image from InputImageReader. Acquires it and add to SettableImageProxyBundle.
    void imageIncoming(ImageReaderProxy imageReader) {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }

            ImageProxy image = null;
            try {
                image = imageReader.acquireNextImage();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to acquire latest image.", e);
            } finally {
                if (image != null) {
                    Integer tag = (Integer) image.getImageInfo().getTag();
                    if (!mCaptureIdList.contains(tag)) {
                        Log.w(TAG, "ImageProxyBundle does not contain this id: " + tag);
                        image.close();
                        return;
                    }

                    mSettableImageProxyBundle.addImageProxy(image);
                }
            }
        }
    }
}
