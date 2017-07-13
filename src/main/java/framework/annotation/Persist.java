package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import framework.Db;
import framework.Try.TriConsumer;

/**
 * Initial Data
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Persist {
    /**
     * @return field names(for insert table(?))
     */
    String field() default "";

    /**
     * @return values(for insert table(...) values(?))
     */
    String[] value() default {};

    /**
     * @return Loader
     */
    Class<? extends TriConsumer<Db, String, Persist>> loader() default Default.class;

    /**
     * Default loader
     */
    static class Default implements TriConsumer<Db, String, Persist> {
        @Override
        public void accept(Db db, String table, Persist persist) {
            db.insert(table, persist.field(), persist.value());
        }
    }
}
