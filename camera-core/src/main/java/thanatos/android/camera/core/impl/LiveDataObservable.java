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

package thanatos.android.camera.core.impl;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.os.SystemClock;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import thanatos.android.camera.core.Observable;
import thanatos.android.camera.core.external.Preconditions;
import thanatos.android.camera.core.external.futures.CallbackToFutureAdapter;
import thanatos.android.camera.core.impl.utils.executor.CameraXExecutors;

/**
 * An observable implemented using {@link LiveData}.
 *
 * <p>While this class can provide error reporting, it is prone to other issues. First, all updates
 * will originate from the main thread before being sent to the observer's executor. Second, there
 * exists the possibility of error and value elision. This means that some posted values and some
 * posted errors may be ignored if a newer error/value is posted before the observers can be
 * updated. If it is important for observers to receive all updates, then this class should not be
 * used.
 *
 * @param <T> The data type used for
 *            {@link Observer#onNewData(Object)}.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class LiveDataObservable<T> implements Observable<T> {


    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    final MutableLiveData<Result<T>> mLiveData = new MutableLiveData<>();
    @GuardedBy("mObservers")
    private final Map<Observer<T>, LiveDataObserverAdapter<T>> mObservers = new HashMap<>();

    /**
     * Posts a new value to be used as the current value of this Observable.
     */
    public void postValue(@Nullable T value) {
        mLiveData.postValue(Result.fromValue(value));
    }

    /**
     * Posts a new error to be used as the current error state of this Observable.
     */
    public void postError(@NonNull Throwable error) {
        mLiveData.postValue(Result.<T>fromError(error));
    }

    /**
     * Returns the underlying {@link LiveData} used to store and update {@link Result Results}.
     */
    @NonNull
    public LiveData<Result<T>> getLiveData() {
        return mLiveData;
    }

    @NonNull
    @Override
    public ListenableFuture<T> fetchData() {
        return CallbackToFutureAdapter.getFuture(new CallbackToFutureAdapter.Resolver<T>() {
            @Nullable
            @Override
            public Object attachCompleter(
                    @NonNull final CallbackToFutureAdapter.Completer<T> completer) {
                CameraXExecutors.mainThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        Result<T> result = mLiveData.getValue();
                        Throwable error;
                        if (result == null) {
                            completer.setException(new IllegalStateException(
                                    "Observable has not yet been initialized with a value."));
                        } else if (result.completedSuccessfully()) {
                            completer.set(result.getValue());
                        } else {
                            Preconditions.checkNotNull(result.getError());
                            completer.setException(result.getError());
                        }
                    }
                });

                return LiveDataObservable.this + " [fetch@" + SystemClock.uptimeMillis() + "]";
            }
        });
    }

    @SuppressLint("LambdaLast") // Remove after https://issuetracker.google.com/135275901
    @Override
    public void addObserver(@NonNull Executor executor, @NonNull Observer<T> observer) {
        synchronized (mObservers) {
            final LiveDataObserverAdapter<T> oldAdapter = mObservers.get(observer);
            if (oldAdapter != null) {
                oldAdapter.disable();
            }

            final LiveDataObserverAdapter<T> newAdapter = new LiveDataObserverAdapter<>(executor,
                    observer);
            mObservers.put(observer, newAdapter);

            CameraXExecutors.mainThreadExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    mLiveData.removeObserver(oldAdapter);
                    mLiveData.observeForever(newAdapter);
                }
            });
        }
    }

    @Override
    public void removeObserver(@NonNull Observer<T> observer) {
        synchronized (mObservers) {
            final LiveDataObserverAdapter<T> adapter = mObservers.remove(observer);

            if (adapter != null) {
                adapter.disable();
                CameraXExecutors.mainThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        mLiveData.removeObserver(adapter);
                    }
                });
            }
        }
    }

    /**
     * A wrapper class that allows error reporting.
     *
     * A Result can contain either a value or an error, but not both.
     *
     * @param <T> The data type used for
     *            {@link Observer#onNewData(Object)}.
     */
    public static final class Result<T> {
        @Nullable
        private T mValue;
        @Nullable
        private Throwable mError;

        private Result(@Nullable T value, @Nullable Throwable error) {
            mValue = value;
            mError = error;
        }

        /**
         * Creates a successful result that contains a value.
         */
        static <T> Result<T> fromValue(@Nullable T value) {
            return new Result<>(value, null);
        }

        /**
         * Creates a failed result that contains an error.
         */
        static <T> Result<T> fromError(@NonNull Throwable error) {
            return new Result<>(null, Preconditions.checkNotNull(error));
        }

        /**
         * Returns whether this result contains a value or an error.
         *
         * <p>A successful result will contain a value.
         */
        public boolean completedSuccessfully() {
            return mError == null;
        }

        /**
         * Returns the value contained within this result.
         *
         * @throws IllegalStateException if the result contains an error rather than a value.
         */
        @Nullable
        public T getValue() {
            if (!completedSuccessfully()) {
                throw new IllegalStateException(
                        "Result contains an error. Does not contain a value.");
            }

            return mValue;
        }

        /**
         * Returns the error contained within this result, or {@code null} if the result contains
         * a value.
         */
        @Nullable
        public Throwable getError() {
            return mError;
        }
    }

    private static final class LiveDataObserverAdapter<T> implements
            android.arch.lifecycle.Observer<Result<T>> {

        final AtomicBoolean mActive = new AtomicBoolean(true);
        final Observer<T> mObserver;
        final Executor mExecutor;

        LiveDataObserverAdapter(@NonNull Executor executor, @NonNull Observer<T> observer) {
            mExecutor = executor;
            mObserver = observer;
        }

        void disable() {
            mActive.set(false);
        }

        @Override
        public void onChanged(@NonNull final Result<T> result) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!mActive.get()) {
                        // Observer has been disabled.
                        return;
                    }

                    if (result.completedSuccessfully()) {
                        mObserver.onNewData(result.getValue());
                    } else {
                        Preconditions.checkNotNull(result.getError());
                        mObserver.onError(result.getError());
                    }
                }
            });
        }
    }
}
