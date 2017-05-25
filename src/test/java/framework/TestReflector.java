package framework;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import framework.annotation.Help;

/**
 * Reflector test
 */
public class TestReflector extends Tester {
    @SuppressWarnings("javadoc")
    @Help("from")
    static class Foo {
        @Help("abc")
        int n;

        @Help("111")
        Foo() {
        }

        @Help("+")
        void bar() {
        }
    }

    {
        groupWith("Reflector", g -> {
            expect(g + ":changeAnnotation:class", () -> {
                Class<?> target = Foo.class;
                Reflector.chagneAnnotation(target, Help.class, new Help() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return getClass();
                    }

                    @Override
                    public String[] value() {
                        return Tool.array("to");
                    }
                });
                return target.getAnnotation(Help.class).value();
            }).toArrayEqual(Tool.array("to"));
            expect(g + ":changeAnnotation:field", () -> {
                Field target = Reflector.field(Foo.class, "n").get();
                Reflector.chagneAnnotation(target, Help.class, new Help() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return getClass();
                    }

                    @Override
                    public String[] value() {
                        return Tool.array("def");
                    }
                });
                return target.getAnnotation(Help.class).value();
            }).toArrayEqual(Tool.array("def"));
            expect(g + ":changeAnnotation:constructor", () -> {
                Constructor<Foo> target = Reflector.constructor(Foo.class).get();
                Reflector.chagneAnnotation(target, Help.class, new Help() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return getClass();
                    }

                    @Override
                    public String[] value() {
                        return Tool.array("222");
                    }
                });
                return target.getAnnotation(Help.class).value();
            }).toArrayEqual(Tool.array("222"));
            expect(g + ":changeAnnotation:method", () -> {
                Method target = Reflector.method(Foo.class, "bar").get();
                Reflector.chagneAnnotation(target, Help.class, new Help() {

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return getClass();
                    }

                    @Override
                    public String[] value() {
                        return Tool.array("def");
                    }
                });
                return target.getAnnotation(Help.class).value();
            }).toArrayEqual(Tool.array("def"));
        });
    }
}
