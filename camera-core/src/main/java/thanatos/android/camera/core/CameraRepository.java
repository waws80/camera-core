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

import android.content.Context;
import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.RestrictTo.Scope;
import android.util.Log;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import thanatos.android.camera.core.external.Preconditions;
import thanatos.android.camera.core.external.futures.CallbackToFutureAdapter;
import thanatos.android.camera.core.impl.utils.executor.CameraXExecutors;
import thanatos.android.camera.core.impl.utils.futures.Futures;

/**
 * A collection of {@link BaseCamera} instances.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public final class CameraRepository implements UseCaseGroup.StateChangeListener {
    private static final String TAG = "CameraRepository";

    private final Object mCamerasLock = new Object();

    @GuardedBy("mCamerasLock")
    private final Map<String, BaseCamera> mCameras = new HashMap<>();
    @GuardedBy("mCamerasLock")
    private final Set<BaseCamera> mReleasingCameras = new HashSet<>();
    @GuardedBy("mCamerasLock")
    private ListenableFuture<Void> mDeinitFuture;
    @GuardedBy("mCamerasLock")
    private CallbackToFutureAdapter.Completer<Void> mDeinitCompleter;

    /**
     * Initializes the repository from a {@link Context}.
     *
     * <p>All cameras queried from the {@link CameraFactory} will be added to the repository.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void init(CameraFactory cameraFactory) {
        synchronized (mCamerasLock) {
            try {
                Set<String> camerasList = cameraFactory.getAvailableCameraIds();
                for (String id : camerasList) {
                    Log.d(TAG, "Added camera: " + id);
                    mCameras.put(id, cameraFactory.getCamera(id));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Unable to enumerate cameras", e);
            }
        }
    }

    /**
     * Clear and release all cameras from the repository.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public ListenableFuture<Void> deinit() {
        synchronized (mCamerasLock) {
            // If the camera list is empty, we can either return the current deinit future that
            // has not yet completed, or an immediate successful future if we are already
            // completely deinitialized.
            if (mCameras.isEmpty()) {
                return mDeinitFuture == null ? Futures.immediateFuture(null) : mDeinitFuture;
            }

            ListenableFuture<Void> currentFuture = mDeinitFuture;
            if (currentFuture == null) {
                // Create a single future that will be used to track closing of all cameras.
                // Once all cameras have been released, this future will complete. This future
                // will stay active until all cameras in mReleasingCameras has completed, even if
                // CameraRepository is initialized and deinitialized multiple times in quick
                // succession.
                currentFuture = CallbackToFutureAdapter.getFuture((completer) -> {
                    Preconditions.checkState(Thread.holdsLock(mCamerasLock));
                    mDeinitCompleter = completer;
                    return "CameraRepository-deinit";
                });
                mDeinitFuture = currentFuture;
            }

            for (final BaseCamera camera : mCameras.values()) {
                // Release the camera and wait for it to complete. We keep track of which cameras
                // are still releasing with mReleasingCameras.
                mReleasingCameras.add(camera);
                camera.release().addListener(() -> {
                    synchronized (mCamerasLock) {
                        // When the camera has completed releasing, we can now remove it from
                        // mReleasingCameras. Any time a camera finishes releasing, we need to
                        // check if all cameras a finished so we can finish the future which is
                        // waiting for all cameras to release.
                        mReleasingCameras.remove(camera);
                        if (mReleasingCameras.isEmpty()) {
                            Preconditions.checkNotNull(mDeinitCompleter);
                            // Every camera has been released. Signal successful completion of
                            // deinit().
                            mDeinitCompleter.set(null);
                            mDeinitCompleter = null;
                            mDeinitFuture = null;
                        }
                    }
                }, CameraXExecutors.directExecutor());
            }

            // Ensure all cameras are removed from the active "mCameras" map. This map can be
            // repopulated by #init().
            mCameras.clear();

            return currentFuture;
        }
    }

    /**
     * Gets a {@link BaseCamera} for the given id.
     *
     * @param cameraId id for the camera
     * @return a {@link BaseCamera} paired to this id
     * @throws IllegalArgumentException if there is no camera paired with the id
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public BaseCamera getCamera(String cameraId) {
        synchronized (mCamerasLock) {
            BaseCamera camera = mCameras.get(cameraId);

            if (camera == null) {
                throw new IllegalArgumentException("Invalid camera: " + cameraId);
            }

            return camera;
        }
    }

    /**
     * Gets the set of all camera ids.
     *
     * @return set of all camera ids
     */
    Set<String> getCameraIds() {
        synchronized (mCamerasLock) {
            return new HashSet<>(mCameras.keySet());
        }
    }

    /**
     * Attaches all the use cases in the {@link UseCaseGroup} and opens all the associated cameras.
     *
     * <p>This will start streaming data to the uses cases which are also online.
     */
    @Override
    public void onGroupActive(UseCaseGroup useCaseGroup) {
        synchronized (mCamerasLock) {
            Map<String, Set<UseCase>> cameraIdToUseCaseMap = useCaseGroup.getCameraIdToUseCaseMap();
            for (Map.Entry<String, Set<UseCase>> cameraUseCaseEntry :
                    cameraIdToUseCaseMap.entrySet()) {
                BaseCamera camera = getCamera(cameraUseCaseEntry.getKey());
                attachUseCasesToCamera(camera, cameraUseCaseEntry.getValue());
            }
        }
    }

    /** Attaches a set of use cases to a camera. */
    @GuardedBy("mCamerasLock")
    private void attachUseCasesToCamera(BaseCamera camera, Set<UseCase> useCases) {
        camera.addOnlineUseCase(useCases);
    }

    /**
     * Detaches all the use cases in the {@link UseCaseGroup} and closes the camera with no attached
     * use cases.
     */
    @Override
    public void onGroupInactive(UseCaseGroup useCaseGroup) {
        synchronized (mCamerasLock) {
            Map<String, Set<UseCase>> cameraIdToUseCaseMap = useCaseGroup.getCameraIdToUseCaseMap();
            for (Map.Entry<String, Set<UseCase>> cameraUseCaseEntry :
                    cameraIdToUseCaseMap.entrySet()) {
                BaseCamera camera = getCamera(cameraUseCaseEntry.getKey());
                detachUseCasesFromCamera(camera, cameraUseCaseEntry.getValue());
            }
        }
    }

    /** Detaches a set of use cases from a camera. */
    @GuardedBy("mCamerasLock")
    private void detachUseCasesFromCamera(BaseCamera camera, Set<UseCase> useCases) {
        camera.removeOnlineUseCase(useCases);
    }
}
