package com.mindsetalliance.core.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermissions {
    String[] value();

    /** VIEW / CREATE / UPDATE / DELETE — vide = permission globale (4 actions si décomposable). */
    String action() default "";
}
