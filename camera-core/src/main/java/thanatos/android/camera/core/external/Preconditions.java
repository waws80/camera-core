package thanatos.android.camera.core.external;


import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.text.TextUtils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

@RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
public class Preconditions {
    public static void checkArgument(boolean expression) {
        if (!expression) {
            throw new IllegalArgumentException();
        }
    }

    public static void checkArgument(boolean expression, Object errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }

    @NonNull
    public static <T extends CharSequence> T checkStringNotEmpty(T string) {
        if (TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException();
        } else {
            return string;
        }
    }

    @NonNull
    public static <T extends CharSequence> T checkStringNotEmpty(T string, Object errorMessage) {
        if (TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        } else {
            return string;
        }
    }

    @NonNull
    public static <T> T checkNotNull(T reference) {
        if (reference == null) {
            throw new NullPointerException();
        } else {
            return reference;
        }
    }

    @NonNull
    public static <T> T checkNotNull(T reference, Object errorMessage) {
        if (reference == null) {
            throw new NullPointerException(String.valueOf(errorMessage));
        } else {
            return reference;
        }
    }

    public static void checkState(boolean expression, String message) {
        if (!expression) {
            throw new IllegalStateException(message);
        }
    }

    public static void checkState(boolean expression) {
        checkState(expression, (String)null);
    }

    public static int checkFlagsArgument(int requestedFlags, int allowedFlags) {
        if ((requestedFlags & allowedFlags) != requestedFlags) {
            throw new IllegalArgumentException("Requested flags 0x" + Integer.toHexString(requestedFlags) + ", but only 0x" + Integer.toHexString(allowedFlags) + " are allowed");
        } else {
            return requestedFlags;
        }
    }

    @IntRange(
            from = 0L
    )
    public static int checkArgumentNonnegative(int value, String errorMessage) {
        if (value < 0) {
            throw new IllegalArgumentException(errorMessage);
        } else {
            return value;
        }
    }

    @IntRange(
            from = 0L
    )
    public static int checkArgumentNonnegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        } else {
            return value;
        }
    }

    public static long checkArgumentNonnegative(long value) {
        if (value < 0L) {
            throw new IllegalArgumentException();
        } else {
            return value;
        }
    }

    public static long checkArgumentNonnegative(long value, String errorMessage) {
        if (value < 0L) {
            throw new IllegalArgumentException(errorMessage);
        } else {
            return value;
        }
    }

    public static int checkArgumentPositive(int value, String errorMessage) {
        if (value <= 0) {
            throw new IllegalArgumentException(errorMessage);
        } else {
            return value;
        }
    }

    public static float checkArgumentFinite(float value, String valueName) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException(valueName + " must not be NaN");
        } else if (Float.isInfinite(value)) {
            throw new IllegalArgumentException(valueName + " must not be infinite");
        } else {
            return value;
        }
    }

    public static float checkArgumentInRange(float value, float lower, float upper, String valueName) {
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException(valueName + " must not be NaN");
        } else if (value < lower) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s is out of range of [%f, %f] (too low)", valueName, lower, upper));
        } else if (value > upper) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s is out of range of [%f, %f] (too high)", valueName, lower, upper));
        } else {
            return value;
        }
    }

    public static int checkArgumentInRange(int value, int lower, int upper, String valueName) {
        if (value < lower) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s is out of range of [%d, %d] (too low)", valueName, lower, upper));
        } else if (value > upper) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s is out of range of [%d, %d] (too high)", valueName, lower, upper));
        } else {
            return value;
        }
    }

    public static long checkArgumentInRange(long value, long lower, long upper, String valueName) {
        if (value < lower) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s is out of range of [%d, %d] (too low)", valueName, lower, upper));
        } else if (value > upper) {
            throw new IllegalArgumentException(String.format(Locale.US, "%s is out of range of [%d, %d] (too high)", valueName, lower, upper));
        } else {
            return value;
        }
    }

    public static <T> T[] checkArrayElementsNotNull(T[] value, String valueName) {
        if (value == null) {
            throw new NullPointerException(valueName + " must not be null");
        } else {
            for(int i = 0; i < value.length; ++i) {
                if (value[i] == null) {
                    throw new NullPointerException(String.format(Locale.US, "%s[%d] must not be null", valueName, i));
                }
            }

            return value;
        }
    }

//    @NonNull
//    public static <C extends Collection<T>, T> C checkCollectionElementsNotNull(C value, String valueName) {
//        if (value == null) {
//            throw new NullPointerException(valueName + " must not be null");
//        } else {
//            long ctr = 0L;
//
//            for(Iterator var4 = value.iterator(); var4.hasNext(); ++ctr) {
//                T elem = (T) var4.next();
//                if (elem == null) {
//                    throw new NullPointerException(String.format(Locale.US, "%s[%d] must not be null", valueName, ctr));
//                }
//            }
//
//            return value;
//        }
//    }

    public static <T> Collection<T> checkCollectionNotEmpty(Collection<T> value, String valueName) {
        if (value == null) {
            throw new NullPointerException(valueName + " must not be null");
        } else if (value.isEmpty()) {
            throw new IllegalArgumentException(valueName + " is empty");
        } else {
            return value;
        }
    }

    public static float[] checkArrayElementsInRange(float[] value, float lower, float upper, String valueName) {
        checkNotNull(value, valueName + " must not be null");

        for(int i = 0; i < value.length; ++i) {
            float v = value[i];
            if (Float.isNaN(v)) {
                throw new IllegalArgumentException(valueName + "[" + i + "] must not be NaN");
            }

            if (v < lower) {
                throw new IllegalArgumentException(String.format(Locale.US, "%s[%d] is out of range of [%f, %f] (too low)", valueName, i, lower, upper));
            }

            if (v > upper) {
                throw new IllegalArgumentException(String.format(Locale.US, "%s[%d] is out of range of [%f, %f] (too high)", valueName, i, lower, upper));
            }
        }

        return value;
    }

    private Preconditions() {
    }
}
