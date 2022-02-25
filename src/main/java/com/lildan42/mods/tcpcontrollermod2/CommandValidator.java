package com.lildan42.mods.tcpcontrollermod2;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CommandValidator {
    String command();
    int minArgs() default 0;
}
