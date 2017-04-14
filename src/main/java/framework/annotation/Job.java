package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import framework.Config;
import framework.Db;
import framework.Lazy;
import framework.Reflector;
import framework.Tool;
import framework.Tuple;

/**
 * Setting for job schedule.
 * <ul>
 * <li>{@code @Job("1H")} run every one hour(usable unit is D, H, M, S)</li>
 * <li>{@code @Job("2")} run on every month 2nd</li>
 * <li>{@code @Job("12:00")} run 12:00 on every day</li>
 * <li>{@code @Job(":01")} run 1 minute on every hour</li>
 * <li>{@code @Job("12:34:56")} run 12:34:56 on every day</li>
 * <li>{@code @Job("Fri 12:34")} run 12:34 on every Friday</li>
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
         * 
         * @param classes target class
         */
        public static void setup(Class<?>... classes) {
            ZonedDateTime now = ZonedDateTime.now();
            for (Class<?> c : classes) {
                Stream.of(c.getDeclaredMethods()).map(method -> Tuple.of(method, method.getAnnotation(Job.class))).filter(pair -> pair.r != null)
                        .forEach(pair -> {
                            Method method = pair.l;
                            Stream.of(pair.r.value().split("\\s*,\\s*")).filter(Tool.notEmpty)
                                    .<String>map(j -> j.startsWith("job.") ? Config.find(j).orElse("") : j).filter(Tool.notEmpty).forEach(text -> {
                                        if (scheduler.get() == null) {
                                            int n = Config.app_job_threads.integer();
                                            scheduler.set(Executors.newScheduledThreadPool(n));
                                            Tool.getLogger().info(n + " job threads created");
                                        }
                                        String name = c.getName() + '.' + method.getName();
                                        long first = Tool.nextMillis(text, now);
                                        ZonedDateTime firstStart = now.plus(first, ChronoUnit.MILLIS);
                                        Tool.getLogger().info(name + " : job next start at " + firstStart);
                                        scheduler.get().schedule(new Runnable() {
                                            ZonedDateTime start = firstStart;

                                            @Override
                                            public void run() {
                                                Logger logger = Tool.getLogger();
                                                logger.info(name + " : job start - " + start);
                                                try (Lazy<Db> db = new Lazy<>(Db::connect)) {
                                                    method.setAccessible(true);
                                                    method.invoke(Modifier.isStatic(method.getModifiers()) ? null : Reflector.instance(c),
                                                            Stream.of(method.getParameters()).map(p -> {
                                                                Class<?> type = p.getType();
                                                                if (type == ZonedDateTime.class) {
                                                                    return start;
                                                                }
                                                                if (type == LocalDateTime.class) {
                                                                    return start.toLocalDateTime();
                                                                }
                                                                if (type == OffsetDateTime.class) {
                                                                    return start.toOffsetDateTime();
                                                                }
                                                                if (type == Date.class) {
                                                                    return Date.from(start.toInstant());
                                                                }
                                                                if (Db.class.isAssignableFrom(type)) {
                                                                    return db.get();
                                                                }
                                                                return null;
                                                            }).toArray());
                                                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                                                    logger.log(Level.WARNING, name + " : job error", e);
                                                }
                                                ZonedDateTime end = ZonedDateTime.now();
                                                logger.info(name + " : job end - " + end + " (" + Duration.between(start, end) + ")");
                                                long next = Tool.nextMillis(text, end);
                                                if (next < 1000) {
                                                    next = Tool.nextMillis(text, end.plusSeconds(1));
                                                }
                                                start = end.plus(next, ChronoUnit.MILLIS);
                                                logger.info(name + " : job next start at " + start);
                                                scheduler.get().schedule(this, next, TimeUnit.MILLISECONDS);
                                            }
                                        }, first, TimeUnit.MILLISECONDS);
                                    });
                        });
            }
        }

        /**
         * shutdown
         */
        public static void shutdown() {
            Optional.ofNullable(scheduler.get()).ifPresent(ScheduledExecutorService::shutdown);
        }
    }
}
