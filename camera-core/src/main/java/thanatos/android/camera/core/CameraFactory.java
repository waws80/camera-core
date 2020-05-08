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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import java.util.Set;

/**
 * The factory class that creates {@link BaseCamera} instances.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface CameraFactory {

    /** Gets the camera with the associated id. */
    BaseCamera getCamera(String cameraId);

    /** Gets ids for all the available cameras. */
    Set<String> getAvailableCameraIds() throws CameraInfoUnavailableException;

    /**
     * Gets the id of the camera with the specified lens facing. Returns null if there's no
     * camera with specified lens facing.
     */
    @Nullable
    String cameraIdForLensFacing(@NonNull CameraX.LensFacing lensFacing)
            throws CameraInfoUnavailableException;

    /** Gets a {@link LensFacingCameraIdFilter} with specified lens facing. */
    @NonNull
    LensFacingCameraIdFilter getLensFacingCameraIdFilter(@NonNull CameraX.LensFacing lensFacing);
}
