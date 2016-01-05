package org.sjsmp.server;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SjsmpAction
{
	String value();	//actually it's a description, but we use value() as it's a default name
	boolean requireConfirm() default false;
}
