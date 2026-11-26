package com.campusflow.modules.user.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BcryptPassword.Validator.class)
public @interface BcryptPassword {

    String message() default "密码长度或编码不符合要求";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int minLength() default 12;

    final class Validator implements ConstraintValidator<BcryptPassword, String> {
        private int minLength;

        @Override
        public void initialize(BcryptPassword constraint) {
            minLength = constraint.minLength();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            // Bound the raw UTF-16 length before encoding, then enforce BCrypt's UTF-8 byte limit.
            if (value.length() > 128 || value.isBlank() || value.indexOf(0) >= 0) {
                return false;
            }
            int length = value.codePointCount(0, value.length());
            return length >= minLength && length <= 64
                    && StandardCharsets.UTF_8.newEncoder().canEncode(value)
                    && value.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
}
