package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * allowed or deny letters
 */
@Target({ ElementType.PARAMETER, ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Letters {
    /**
     * @return apply groups
     */
    Class<? extends Valid.All>[] groups() default {};

    /**
     * @return target letters
     */
    String value();

    /**
     * @return true if exclude letters, else include letters
     */
    boolean deny() default false;

    /**
     * 0 - 9
     */
    static final String DIGITS = "0123456789";

    /**
     * 0 - 9 &amp; sign
     */
    static final String INTEGER = "0123456789+-";

    /**
     * 0 - 9 &amp; sign &amp; period
     */
    static final String REAL = "0123456789+-.";

    /**
     * katakana &amp; middle point &amp; space
     */
    static final String KATAKANA = "アイウエオヴァィゥェォカキクケコガギグゲゴサシスセソザジズゼゾタチツテトダヂヅデドナニヌネノハヒフヘホバビブベボパピプペポマミムメモヤユヨャュョラリルレロワヲンヮー・　";

    /**
     * hiragana &amp; middle point &amp; space
     */
    static final String HIRAGANA = "あいうえおぁぃぅぇぉかきくけこがぎぐげごさしすせそざじずぜぞたちつてとだぢづでどなにぬねのはひふへほばびぶべぼぱぴぷぺぽまみむめもやゆよゃゅょらりるれろわをんゎー・　";
}
