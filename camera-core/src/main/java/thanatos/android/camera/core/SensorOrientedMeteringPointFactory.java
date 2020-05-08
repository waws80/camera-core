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

import android.graphics.PointF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Rational;
import android.util.Size;

import java.util.Set;

/**
 * A {@link MeteringPointFactory} that can create {@link MeteringPoint} by sensor oriented x, y ,
 * width and height.
 *
 * <p>This factory is suitable for apps that already have coordinates translated into sensor
 * coordinates. It is also useful for apps that want to focus on something detected in
 * {@link ImageAnalysis}. Apps can pass the {@link ImageAnalysis} instance for useCaseForFOV
 * argument and CameraX will then adjust the final sensor coordinates by aspect ratio of
 * ImageAnalysis.
 */
public class SensorOrientedMeteringPointFactory extends MeteringPointFactory {
    /** the logical width of FoV in sensor orientation*/
    private final float mWidth;
    /** the logical height of FoV in sensor orientation */
    private final float mHeight;

    /**
     * Creates the SensorOrientedMeteringPointFactory by width and height
     *
     * <p>The width/height is the logical width/height of the preview FoV in sensor orientation and
     * X/Y is the logical XY inside the FOV. User can set the width and height to 1.0 which will
     * make the XY the normalized coordinates [0..1].
     *
     * <p>By default, it will use active {@link Preview} as the FoV for final coordinates
     * translation.
     *
     * @param width the logical width of FoV in sensor orientation
     * @param height the logical height of FoV in sensor orientation
     */
    public SensorOrientedMeteringPointFactory(float width, float height) {
        mWidth = width;
        mHeight = height;
        mFOVAspectRatio = null;
    }

    /**
     * Creates the SensorOrientedMeteringPointFactory by width, height and useCaseForFOV.
     *
     * <p>The width/height is the logical width/height of the preview FoV in sensor orientation and
     * X/Y is the logical XY inside the FOV. User can set the width and height to 1.0 which will
     * make the XY the normalized coordinates [0..1].
     *
     * <p>useCaseForFOV is used to determine the FOV of this translation. This useCaseForFOV needs
     * to be bound via {@link CameraX#bindToLifecycle(LifecycleOwner, UseCase...)} first. Otherwise
     * it will throw a {@link IllegalStateException}
     *
     * @param width the logical width of FOV in sensor orientation.
     * @param height the logical height of FOV in sensor orientation.
     * @param useCaseForFOV the {@link UseCase} to be the FOV.
     */
    public SensorOrientedMeteringPointFactory(float width, float height,
            @NonNull UseCase useCaseForFOV) {
        mWidth = width;
        mHeight = height;
        mFOVAspectRatio = getUseCaseAspectRatio(useCaseForFOV);
    }

    @Nullable
    private Rational getUseCaseAspectRatio(@Nullable UseCase useCase) {
        if (useCase == null) {
            return null;
        }

        Set<String> cameraIds = useCase.getAttachedCameraIds();
        if (cameraIds.isEmpty()) {
            throw new IllegalStateException("UseCase " + useCase + " is not bound.");
        }

        for (String id : cameraIds) {
            Size resolution = useCase.getAttachedSurfaceResolution(id);
            // Returns an aspect ratio of first found attachedSurfaceResolution.
            return new Rational(resolution.getWidth(), resolution.getHeight());
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    protected PointF translatePoint(float x, float y) {
        PointF pt = new PointF(x / mWidth, y / mHeight);
        return pt;
    }

}
