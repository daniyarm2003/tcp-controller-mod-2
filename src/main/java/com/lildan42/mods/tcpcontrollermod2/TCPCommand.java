package com.lildan42.mods.tcpcontrollermod2;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface TCPCommand {
    int id();
    int minArgs() default 1;
    int maxArgs() default Integer.MAX_VALUE;
}
