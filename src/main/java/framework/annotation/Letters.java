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
     * katakana &amp; middle point &amp; space
     */
    String KATAKANA = "アイウエオヴァィゥェォカキクケコガギグゲゴサシスセソザジズゼゾタチツテトダヂヅデドナニヌネノハヒフヘホバビブベボパピプペポマミムメモヤユヨャュョラリルレロワヲンヮー・　";

    /**
     * hiragana &amp; middle point &amp; space
     */
    String HIRAGANA = "あいうえおぁぃぅぇぉかきくけこがぎぐげごさしすせそざじずぜぞたちつてとだぢづでどなにぬねのはひふへほばびぶべぼぱぴぷぺぽまみむめもやゆよゃゅょらりるれろわをんゎー・　";
}
