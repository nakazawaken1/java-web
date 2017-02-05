package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import framework.Config;
import framework.Tool;

/**
 * Setting for job schedule.
 * <ul>
 * <li>{@code @Job("1H")} run every one hour(usable unit is D, H, M, S)</li>
 * <li>{@code @Job("2")} run on every month 2nd</li>
 * <li>{@code @Job("12:00")} run 12:00 on every day</li>
 * <li>{@code @Job("12:34:56")} run 12:34:56 on every day</li>
 * <li>{@code @Job("3 10:00")} run 10:00 on every month 3rd</li>
 * <li>{@code @Job("2/3 10:00")} run 10:00 on February 3rd</li>
 * <li>{@code @Job("12:00, 13:00")} run 12:00 and 13:00 on every day</li>
 * <li>{@code @Job("job.daily")} run at config.txt setting value</li>
 * </ul>
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Job {
    /**
     * @return schedule
     */
    String value();

    /**
     * job scheduler
     */
    public static class Scheduler {
        /**
         * scheduler
         */
        static final AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>();

        /**
         * setup
         * @param classes target class
         */
        public static void setup(Class<?>... classes) {
            for (Class<?> c : classes) {
                Stream.of(c.getDeclaredMethods()).map(method -> Tool.pair(method, method.getAnnotation(Job.class))).filter(pair -> pair.getValue() != null)
                        .forEach(pair -> {
                            Method method = pair.a;
                            Stream.of(pair.getValue().value().split("\\s*,\\s*")).filter(Tool.notEmpty)
                                    .<String>map(j -> j.startsWith("job.") ? Config.find(j).orElse("") : j).filter(Tool.notEmpty).forEach(text -> {
                                        if (scheduler.get() == null) {
                                            int n = Config.app_job_threads.integer();
                                            scheduler.set(Executors.newScheduledThreadPool(n));
                                            Logger.getGlobal().info(n + " job threads created");
                                        }
                                        scheduler.get().schedule(new Runnable() {

                                            @Override
                                            public void run() {
                                                long next = Tool.nextMillis(text, ZonedDateTime.now());
                                                if (next < 1000) {
                                                    next = Tool.nextMillis(text, ZonedDateTime.now().plusSeconds(1));
                                                }
                                                try {
                                                    method.invoke(Modifier.isStatic(method.getModifiers()) ? null : c.newInstance());
                                                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                                                        | InstantiationException e) {
                                                    Logger.getGlobal().log(Level.WARNING, "job error", e);
                                                }
                                                scheduler.get().schedule(this, next, TimeUnit.MILLISECONDS);
                                            }
                                        }, Tool.nextMillis(text, ZonedDateTime.now()), TimeUnit.MILLISECONDS);
                                    });
                        });
            }
        }
        
        /**
         * shutdown
         */
        public static void shutdown() {
            scheduler.get().shutdown();
        }
    }
}
