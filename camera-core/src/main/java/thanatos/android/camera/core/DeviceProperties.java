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

import android.os.Build;
import android.support.annotation.RestrictTo;


import com.google.auto.value.AutoValue;

/**
 * Container of the device properties.
 *
 * @hide
 */
@AutoValue
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class DeviceProperties {
    /** Creates an instance by querying the properties from {@link Build}. */
    public static DeviceProperties create() {
        return create(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT);
    }

    /** Creates an instance from the given properties. */
    public static DeviceProperties create(String manufacturer, String model, int sdkVersion) {
        return new AutoValue_DeviceProperties(manufacturer, model, sdkVersion);
    }

    /** Returns the manufacturer of the device. */
    public abstract String manufacturer();

    /** Returns the model of the device. */
    public abstract String model();

    /** Returns the SDK version of the OS running on the device. */
    public abstract int sdkVersion();
}
