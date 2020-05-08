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
import android.support.annotation.RestrictTo;

import java.util.Set;

/**
 * A filter selects certain type of camera id from a camera id set.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface CameraIdFilter {
    /**
     * Returns a set of camera id with the same type.
     *
     * @param cameraIds the camera id set to be filtered.
     * @return the available camera id set.
     */
    @NonNull
    Set<String> filter(@NonNull Set<String> cameraIds);
}
