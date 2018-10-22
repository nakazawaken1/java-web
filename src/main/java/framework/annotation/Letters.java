package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.AbstractValidator;
import framework.annotation.Validator.ErrorAppender;

/**
 * allowed or deny letters
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
@Validator(Letters.Validator.class)
public @interface Letters {
    /**
     * @return Apply groups
     */
    Class<? extends Valid.All>[] groups() default Valid.All.class;

    /**
     * @return Target letters
     */
    String value();

    /**
     * @return True if exclude letters, else include letters
     */
    boolean deny() default false;

    /**
     * @return Error message
     */
    String message() default "{Sys.Alert.letters}";

    /**
     * 0 - 9
     */
    String DIGITS = "0123456789";

    /**
     * 0 - 9 &amp; sign
     */
    String INTEGER = "0123456789+-";

    /**
     * 0 - 9 &amp; sign &amp; period
     */
    String REAL = "0123456789+-.";

    /**
     * Carriage Return
     */
    String CR = "\r";

    /**
     * Line Feed
     */
    String LF = "\n";

    /**
     * Carriage Return and Line Feed
     */
    String CRLF = CR + LF;

    /**
     * mark characters
     */
    String MARKS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    /**
     * Upper case alphabet characters
     */
    String ALPHABET_UPPERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Lower case alphabet characters
     */
    String ALPHABET_LOWERS = "abcdefghijklmnopqrstuvwxyz";

    /**
     * alphabet characters
     */
    String ALPHABETS = ALPHABET_UPPERS + ALPHABET_LOWERS;

    /**
     * alphabet number characters
     */
    String ALPHABETS_NUMBERS = ALPHABETS + DIGITS;

    /**
     * Ascii characters
     */
    String ASCII = MARKS + DIGITS + ALPHABETS;

    /**
     * katakana &amp; middle point &amp; space
     */
    String KATAKANA = "アイウエオヴァィゥェォカキクケコガギグゲゴサシスセソザジズゼゾタチツテトダヂヅデドナニヌネノハヒフヘホバビブベボパピプペポマミムメモヤユヨャュョラリルレロワヲンヮー・　";

    /**
     * hiragana &amp; middle point &amp; space
     */
    String HIRAGANA = "あいうえおぁぃぅぇぉかきくけこがぎぐげごさしすせそざじずぜぞたちつてとだぢづでどなにぬねのはひふへほばびぶべぼぱぴぷぺぽまみむめもやゆよゃゅょらりるれろわをんゎー・　";

    @SuppressWarnings("javadoc")
    class Validator extends AbstractValidator<Letters> {

        public Validator(Letters annotation) {
            super(annotation);
        }

        @Override
        protected void validate(String name, String value, ErrorAppender appender) {
            if (value != null && (value.chars().anyMatch(annotation.deny() ? c -> annotation.value().indexOf(c) >= 0 : c -> annotation.value().indexOf(c) < 0))) {
                appender.addError(name, value, annotation.message(), "value", annotation.value(), "deny", annotation.deny());
            }
        }
    }
}
