package framework;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

/**
 * Tester
 */
@RunWith(Tester.Runner.class)
public class Tester {

    /**
     * test stack
     */
    final Deque<Tester> stack;

    /**
     * description
     */
    final Description description;

    /**
     * value getter
     */
    Supplier<Object> getter;

    /**
     * before action
     */
    Runnable before;

    /**
     * test
     */
    Consumer<RunNotifier> test;

    /**
     * after action
     */
    Runnable after;

    /**
     * children
     */
    List<Tester> children;

    /**
     * constructor
     */
    public Tester() {
        if (getClass() != Tester.class) {
            stack = new LinkedList<>();
            stack.push(this);
            description = Description.createSuiteDescription(getClass().getName());
        } else {
            stack = null;
            description = Description.EMPTY;
        }
    }

    /**
     * constructor for group
     * 
     * @param stack test stack
     * @param name test name
     */
    Tester(Deque<Tester> stack, String name) {
        this.stack = stack;
        description = Description.createSuiteDescription(name);
    }

    /**
     * constructor for expect
     * 
     * @param stack test stack
     * @param name test name
     * @param supplier value supplier
     * @param action non-value test action
     */
    Tester(Deque<Tester> stack, String name, Supplier<Object> supplier, Runnable action) {
        this.stack = stack;
        description = Description.createTestDescription(getClass().getSimpleName(), name);
        getter = () -> {
            if (action != null) {
                action.run();
            }
            return supplier != null ? supplier.get() : null;
        };
    }

    /**
     * test for value
     * 
     * @param name test name
     * @param supplier value supplier
     * @return expect object
     */
    public Expect expect(String name, Supplier<Object> supplier) {
        return new Expect(stack.peek().add(new Tester(stack, name, supplier, null)));
    }

    /**
     * test for value
     * 
     * @param name test name
     * @param supplier value supplier
     * @return expect object
     */
    public Expect expectWith(String name, Function<String, Object> supplier) {
        return new Expect(stack.peek().add(new Tester(stack, name, () -> supplier.apply(name), null)));
    }

    /**
     * test for non-value
     * 
     * @param name test name
     * @param action value supplier
     * @return expect object
     */
    public Expect expect(String name, Runnable action) {
        return new Expect(stack.peek().add(new Tester(stack, name, null, action)));
    }

    /**
     * grouping
     * 
     * @param name group name
     * @param action tests
     */
    public void group(String name, Runnable action) {
        stack.push(stack.peek().add(new Tester(stack, name)));
        action.run();
        stack.pop();
    }

    /**
     * set before action
     * 
     * @param action before action
     */
    public void beforeEach(Runnable action) {
        stack.peek().before = action;
    }

    /**
     * set after action
     * 
     * @param action after action
     */
    public void afterEach(Runnable action) {
        stack.peek().after = action;
    }

    /**
     * add child
     * 
     * @param child child
     * @return child
     */
    Tester add(Tester child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        description.addChild(child.description);
        return child;
    }

    /**
     * get value
     * 
     * @return value
     */
    Object get() {
        return getter == null ? null : getter.get();
    }

    /**
     * run
     * 
     * @param notifier notifier
     */
    void run(RunNotifier notifier) {
        notifier.fireTestStarted(description);
        try {
            if (test != null) {
                test.accept(notifier);
            }
            if (children != null) {
                for (Tester child : children) {
                    if (before != null) {
                        before.run();
                    }
                    child.run(notifier);
                    if (after != null) {
                        after.run();
                    }
                }
            }
        } catch (AssertionError e) {
            notifier.fireTestFailure(new Failure(description, e));
        } finally {
            notifier.fireTestFinished(description);
        }
    }

    /**
     * expect object
     */
    public static class Expect {

        /**
         * test
         */
        Tester tester;

        /**
         * constructor
         * 
         * @param tester test
         */
        Expect(Tester tester) {
            this.tester = tester;
        }

        /**
         * get expected value
         * 
         * @param expected expected value(values join with newline)
         * @return expected value
         */
        static Object get(Object... expected) {
            return expected.length == 1 ? expected[0] : Stream.of(expected).map(Objects::toString).collect(Collectors.joining(System.lineSeparator()));
        }

        /**
         * check is NULL
         */
        public void toNull() {
            tester.test = notifier -> Assert.assertNull(tester.get());
        }

        /**
         * check is not NULL
         */
        public void toNotNull() {
            tester.test = notifier -> Assert.assertNotNull(tester.get());
        }

        /**
         * check equals
         * 
         * @param expected expected value
         */
        public void toEqual(Object... expected) {
            tester.test = notifier -> Assert.assertEquals(get(expected), tester.get());
        }

        /**
         * check not equals
         * 
         * @param expected expected value
         */
        public void toNotEqual(Object... expected) {
            tester.test = notifier -> Assert.assertNotEquals(get(expected), tester.get());
        }

        /**
         * check exception
         * 
         * @param expected expected exception
         */
        public void toThrow(Class<? extends Throwable> expected) {
            tester.test = notifier -> {
                try {
                    tester.get();
                } catch (Throwable e) {
                    if (!expected.isAssignableFrom(e.getClass())) {
                        Assert.fail("expected to throw <" + expected.getName() + ">, bat was <" + e.getClass().getName() + ">");
                    }
                }
            };
        }

        /**
         * check standard output
         * 
         * @param expected expected output
         */
        public void toOutput(String... expected) {
            tester.test = notifier -> {
                PrintStream backup = System.out;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (PrintStream out = new PrintStream(buffer)) {
                    System.setOut(out);
                    tester.get();
                    Assert.assertEquals(String.join(System.lineSeparator(), expected), buffer.toString().trim());
                }
                System.setOut(backup);
            };
        }

        /**
         * check standard error
         * 
         * @param expected expected error
         */
        public void toErrorOutput(String... expected) {
            tester.test = notifier -> {
                PrintStream backup = System.err;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (PrintStream out = new PrintStream(buffer)) {
                    System.setErr(out);
                    tester.get();
                    Assert.assertEquals(String.join(System.lineSeparator(), expected), buffer.toString().trim());
                }
                System.setErr(backup);
            };
        }
    }

    /**
     * test runner
     */
    public static class Runner extends org.junit.runner.Runner {

        /**
         * test
         */
        final Tester tester;

        /**
         * constructor
         * 
         * @param testClass target class
         * @throws InstantiationException construction error
         * @throws IllegalAccessException access error
         */
        public Runner(Class<? extends Tester> testClass) throws InstantiationException, IllegalAccessException {
            tester = testClass.newInstance();
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.junit.runner.Runner#getDescription()
         */
        @Override
        public Description getDescription() {
            return tester.description;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.junit.runner.Runner#run(org.junit.runner.notification. RunNotifier)
         */
        @Override
        public void run(RunNotifier notifier) {
            if (tester.description != Description.EMPTY) {
                tester.run(notifier);
            }
        }
    }
}
