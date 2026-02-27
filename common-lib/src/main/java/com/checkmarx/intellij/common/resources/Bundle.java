package com.checkmarx.intellij.common.resources;

import com.checkmarx.intellij.common.utils.Constants;
import com.intellij.AbstractBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ResourceBundle;
import java.util.function.Supplier;

/**
 * Bundle class to obtain properties (translations) from CxBundle.properties.
 */
public final class Bundle extends AbstractBundle {

    private static final Bundle INSTANCE = new Bundle();

    private Bundle() {
        super(Constants.BUNDLE_PATH);
    }

    /**
     * {@link AbstractBundle#message(ResourceBundle, String, Object...)}
     */
    @NotNull
    public static @Nls String message(@NotNull @PropertyKey(resourceBundle = Constants.BUNDLE_PATH) Resource key,
                                      Object @NotNull ... params) {
        return INSTANCE.getMessage(String.valueOf(key), params);
    }

    /**
     * {@link AbstractBundle#getLazyMessage(String, Object...)} (ResourceBundle, String, Object...)}
     */
    @NotNull
    public static Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = Constants.BUNDLE_PATH) Resource key,
                                                       Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(String.valueOf(key), params);
    }

    /**
     * Generates a missing field validation message by field name.
     *
     * @param field field name
     * @return validation message
     */
    @NotNull
    public static @Nls String missingFieldMessage(Resource field) {
        return message(Resource.MISSING_FIELD, message(field));
    }
}
