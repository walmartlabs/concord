package com.walmartlabs.concord.common.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Pattern(regexp = "^[0-9a-zA-Z][0-9a-zA-Z_@.]{2,128}$")
@Constraint(validatedBy = {})
public @interface ConcordKey {

    String message() default "{concord.validation.constraints.ConcordKey.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
