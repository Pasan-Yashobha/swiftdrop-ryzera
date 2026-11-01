package lk.swiftdrop.merchant.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import lk.swiftdrop.merchant.common.Inputs;

/** A phone number already normalised with {@link Inputs#normalisePhone}, such as "+94771234567". */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Pattern(regexp = Inputs.PHONE_REGEX)
public @interface Phone {

    String message() default "must be a phone number such as +94771234567";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
