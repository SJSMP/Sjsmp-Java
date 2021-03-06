package org.sjsmp.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * annotate get{Property}() method, it would then check for existence of set{Property}() method if {@code readonly} is set to false;
 * @author barg_ma
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SjsmpPropertyLimits
{
    double min();
    double max();
}
