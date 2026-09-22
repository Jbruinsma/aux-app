package com.aux.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Like FastAPI's Depends(get_current_user): put on a UserEntity controller parameter
// and CurrentUserResolver fills it from the Bearer token, or responds 401
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
