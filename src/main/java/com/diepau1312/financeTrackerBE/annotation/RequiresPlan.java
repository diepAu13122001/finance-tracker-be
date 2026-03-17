package com.diepau1312.financeTrackerBE.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)       // chỉ dùng trên method
@Retention(RetentionPolicy.RUNTIME) // annotation tồn tại lúc runtime để AOP đọc được
public @interface RequiresPlan {
    String value(); // "PLUS" hoặc "PREMIUM"
}