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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import thanatos.android.camera.core.impl.utils.executor.CameraXExecutors;

/**
 * A configuration used to trigger a focus and/or metering action.
 *
 * <p>To construct a {@link FocusMeteringAction}, apps have to create a {@link Builder} by
 * {@link Builder#from(MeteringPoint)} or {@link Builder#from(MeteringPoint, MeteringMode)}.
 * {@link MeteringPoint} is a point used to specify the focus/metering areas. Apps can use various
 * {@link MeteringPointFactory} to create the points. When the {@link FocusMeteringAction} is built,
 * pass it to {@link CameraControl#startFocusAndMetering(FocusMeteringAction)} to initiate the focus
 * and metering action.
 *
 * <p>The default {@link MeteringMode} is {@link MeteringMode#AF_AE_AWB} which means the point is
 * used for all AF/AE/AWB regions. Apps can set the proper {@link MeteringMode} to optionally
 * exclude some 3A regions. Multiple regions for specific 3A type are also supported via
 * {@link Builder#addPoint(MeteringPoint)} or
 * {@link Builder#addPoint(MeteringPoint, MeteringMode)}. App can also this API to enable
 * different region for AF and AE respectively.
 *
 * <p>If any AF points are specified, it will trigger AF to start a manual AF scan and cancel AF
 * trigger when {@link CameraControl#cancelFocusAndMetering()} is called. When triggering AF is
 * done, it will call the {@link OnAutoFocusListener#onFocusCompleted(boolean)} which is set via
 * {@link Builder#setAutoFocusCallback(OnAutoFocusListener)}.  If AF point is not specified or
 * the action is cancelled before AF is locked, CameraX will call the
 * {@link OnAutoFocusListener#onFocusCompleted(boolean)} with isFocusLocked set to false.
 *
 * <p>App can set a auto-cancel duration to let CameraX call
 * {@link CameraControl#cancelFocusAndMetering()} automatically in the specified duration. By
 * default the auto-cancel duration is 5 seconds. Apps can call {@link Builder#disableAutoCancel()}
 * to disable auto-cancel.
 */
public class FocusMeteringAction {
    static final MeteringMode DEFAULT_METERINGMODE = MeteringMode.AF_AE_AWB;
    static final long DEFAULT_AUTOCANCEL_DURATION = 5000;
    private final List<MeteringPoint> mMeteringPointsAF;
    private final List<MeteringPoint> mMeteringPointsAE;
    private final List<MeteringPoint> mMeteringPointsAWB;
    private final Executor mListenerExecutor;
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    final OnAutoFocusListener mOnAutoFocusListener;
    private final long mAutoCancelDurationInMs;
    private AtomicBoolean mHasNotifiedListener = new AtomicBoolean(false);

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    FocusMeteringAction(Builder builder) {
        mMeteringPointsAF = builder.mMeteringPointsAF;
        mMeteringPointsAE = builder.mMeteringPointsAE;
        mMeteringPointsAWB = builder.mMeteringPointsAWB;
        mListenerExecutor = builder.mListenerExecutor;
        mOnAutoFocusListener = builder.mOnAutoFocusListener;
        mAutoCancelDurationInMs = builder.mAutoCancelDurationInMs;
    }

    /**
     * Returns current {@link OnAutoFocusListener}.
     */
    @Nullable
    public OnAutoFocusListener getOnAutoFocusListener() {
        return mOnAutoFocusListener;
    }

    /**
     * Returns auto-cancel duration.  Returns 0 if auto-cancel is disabled.
     */
    public long getAutoCancelDurationInMs() {
        return mAutoCancelDurationInMs;
    }

    /**
     * Returns all {@link MeteringPoint}s used for AF regions.
     */
    @NonNull
    public List<MeteringPoint> getMeteringPointsAF() {
        return mMeteringPointsAF;
    }

    /**
     * Returns all {@link MeteringPoint}s used for AE regions.
     */
    @NonNull
    public List<MeteringPoint> getMeteringPointsAE() {
        return mMeteringPointsAE;
    }

    /**
     * Returns all {@link MeteringPoint}s used for AWB regions.
     */
    @NonNull
    public List<MeteringPoint> getMeteringPointsAWB() {
        return mMeteringPointsAWB;
    }

    /**
     * Returns if auto-cancel is enabled or not.
     */
    public boolean isAutoCancelEnabled() {
        return mAutoCancelDurationInMs != 0;
    }

    @VisibleForTesting
    Executor getListenerExecutor() {
        return mListenerExecutor;
    }

    /**
     * Notifies current {@link OnAutoFocusListener} and ensures it is called once.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void notifyAutoFocusCompleted(boolean isFocused) {
        if (!mHasNotifiedListener.getAndSet(true)) {
            if (mOnAutoFocusListener != null) {
                mListenerExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mOnAutoFocusListener.onFocusCompleted(isFocused);
                    }
                });
            }
        }
    }

    /**
     * Listener for receiving auto-focus completion event.
     */
    public interface OnAutoFocusListener {
        /**
         * Called when camera auto focus completes or when the action is cancelled before
         * auto-focus completes.
         *
         * @param isFocusLocked true if focus is locked successfully, false otherwise.
         */
        void onFocusCompleted(boolean isFocusLocked);
    }

    /**
     * Focus/Metering mode used to specify which 3A regions is activated for corresponding
     * {@link MeteringPoint}.
     */
    public enum MeteringMode {
        AF_AE_AWB,
        AF_AE,
        AE_AWB,
        AF_AWB,
        AF_ONLY,
        AE_ONLY,
        AWB_ONLY
    }

    /**
     * The builder used to create the {@link FocusMeteringAction}. App must use
     * {@link Builder#from(MeteringPoint)}
     * or {@link Builder#from(MeteringPoint, MeteringMode)} to create the {@link Builder}.
     */
    public static class Builder {
        @SuppressWarnings("WeakerAccess") /* synthetic accessor */
        final List<MeteringPoint> mMeteringPointsAF = new ArrayList<>();
        @SuppressWarnings("WeakerAccess") /* synthetic accessor */
        final List<MeteringPoint> mMeteringPointsAE = new ArrayList<>();
        @SuppressWarnings("WeakerAccess") /* synthetic accessor */
        final List<MeteringPoint> mMeteringPointsAWB = new ArrayList<>();
        @SuppressWarnings("WeakerAccess") /* synthetic accessor */
        OnAutoFocusListener mOnAutoFocusListener = null;
        @SuppressWarnings("WeakerAccess") /* synthetic accessor */
        Executor mListenerExecutor = CameraXExecutors.mainThreadExecutor();
        @SuppressWarnings("WeakerAccess") /* synthetic accessor */
        long mAutoCancelDurationInMs = DEFAULT_AUTOCANCEL_DURATION;

        private Builder(@NonNull MeteringPoint point) {
            this(point, DEFAULT_METERINGMODE);
        }

        private Builder(@NonNull MeteringPoint point, @NonNull MeteringMode mode) {
            addPoint(point, mode);
        }

        /**
         * Creates the Builder from a {@link MeteringPoint} with default {@link MeteringMode}.
         */
        @NonNull
        public static Builder from(@NonNull MeteringPoint meteringPoint) {
            return new Builder(meteringPoint);
        }

        /**
         * Creates the Builder from a {@link MeteringPoint} and {@link MeteringMode}
         */
        @NonNull
        public static Builder from(@NonNull MeteringPoint meteringPoint,
                @NonNull MeteringMode mode) {
            return new Builder(meteringPoint, mode);
        }

        /**
         * Adds another {@link MeteringPoint} with default {@link MeteringMode}.
         */
        @NonNull
        public Builder addPoint(@NonNull MeteringPoint point) {
            return addPoint(point, DEFAULT_METERINGMODE);
        }

        /**
         * Adds another {@link MeteringPoint} with specified {@link MeteringMode}.
         */
        @NonNull
        public Builder addPoint(@NonNull MeteringPoint point, @NonNull MeteringMode mode) {
            if (mode == MeteringMode.AF_AE_AWB
                    || mode == MeteringMode.AF_AE
                    || mode == MeteringMode.AF_AWB
                    || mode == MeteringMode.AF_ONLY) {
                mMeteringPointsAF.add(point);
            }

            if (mode == MeteringMode.AF_AE_AWB
                    || mode == MeteringMode.AF_AE
                    || mode == MeteringMode.AE_AWB
                    || mode == MeteringMode.AE_ONLY) {
                mMeteringPointsAE.add(point);
            }

            if (mode == MeteringMode.AF_AE_AWB
                    || mode == MeteringMode.AE_AWB
                    || mode == MeteringMode.AF_AWB
                    || mode == MeteringMode.AWB_ONLY) {
                mMeteringPointsAWB.add(point);
            }
            return this;
        }

        /**
         * Sets the {@link OnAutoFocusListener} to be notified when auto-focus completes. The
         * listener is called on main thread.
         */
        @NonNull
        public Builder setAutoFocusCallback(@NonNull OnAutoFocusListener listener) {
            mOnAutoFocusListener = listener;
            return this;
        }

        /**
         * Sets the {@link OnAutoFocusListener} to be notified when auto-focus completes. The
         * listener is called on specified {@link Executor}.
         */
        @NonNull
        public Builder setAutoFocusCallback(@NonNull Executor executor,
                @NonNull OnAutoFocusListener listener) {
            mListenerExecutor = executor;
            mOnAutoFocusListener = listener;
            return this;
        }

        /**
         * Sets the auto-cancel duration. After set, {@link CameraControl#cancelFocusAndMetering()}
         * will be called in specified duration. By default, auto-cancel is enabled with 5
         * seconds duration.
         */
        @NonNull
        public Builder setAutoCancelDuration(long duration, @NonNull TimeUnit timeUnit) {
            mAutoCancelDurationInMs = timeUnit.toMillis(duration);
            return this;
        }

        /**
         * Disables the auto-cancel.
         */
        @NonNull
        public Builder disableAutoCancel() {
            mAutoCancelDurationInMs = 0;
            return this;
        }

        /**
         * Builds the {@link FocusMeteringAction} instance.
         */
        @NonNull
        public FocusMeteringAction build() {
            return new FocusMeteringAction(this);
        }

    }
}
