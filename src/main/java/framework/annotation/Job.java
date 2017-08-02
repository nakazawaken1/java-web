package framework.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import app.config.Sys;
import framework.Application;
import framework.Db;
import framework.Lazy;
import framework.Log;
import framework.Reflector;
import framework.Request;
import framework.Session;
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
     * Job on application start
     */
    static String OnApplicationStart = "OnApplicationStart";

    /**
     * Job on application end
     */
    static String OnApplicationEnd = "OnApplicationEnd";

    /**
     * Job on logged in
     */
    static String OnLoggedIn = "OnLoggedIn";

    /**
     * Job on logged out
     */
    static String OnLoggedOut = "OnLoggedOut";

    /**
     * Events
     */
    static List<String> events = Tool.list(OnApplicationStart, OnApplicationEnd, OnLoggedIn, OnLoggedOut);

    /**
     * Job scheduler
     */
    public static class Scheduler {
        /**
         * Scheduler
         */
        static final AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>();

        /**
         * Event map
         */
        public static final Map<String, List<Method>> eventMap = new HashMap<>();

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
                            String value = pair.r.value();
                            if(events.contains(value)) {
                                eventMap.computeIfAbsent(value, k -> Tool.list()).add(method);
                                return;
                            }
                            Stream.of(value.split("\\s*,\\s*")).filter(Tool.notEmpty)
                                    .<String>map(
                                            j -> j.startsWith("job.") ? Config.Injector.getSource(Sys.class, Session.currentLocale()).getProperty(j, "") : j)
                                    .filter(Tool.notEmpty).forEach(text -> {
                                        if (scheduler.get() == null) {
                                            int n = Sys.job_threads;
                                            scheduler.set(Executors.newScheduledThreadPool(n));
                                            Log.info(n + " job threads created");
                                        }
                                        String name = c.getName() + '.' + method.getName();
                                        long first = Tool.nextMillis(text, now);
                                        ZonedDateTime firstStart = now.plus(first, ChronoUnit.MILLIS);
                                        Log.info(name + " : job next start at " + firstStart);
                                        scheduler.get().schedule(new Runnable() {
                                            ZonedDateTime start = firstStart;

                                            @Override
                                            public void run() {
                                                Log.info(name + " : job start - " + start);
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
                                                    Log.warning(e, () -> name + " : job error");
                                                }
                                                ZonedDateTime end = ZonedDateTime.now();
                                                Log.info(name + " : job end - " + end + " (" + Duration.between(start, end) + ")");
                                                long next = Tool.nextMillis(text, end);
                                                if (next < 1000) {
                                                    next = Tool.nextMillis(text, end.plusSeconds(1));
                                                }
                                                start = end.plus(next, ChronoUnit.MILLIS);
                                                Log.info(name + " : job next start at " + start);
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
            Tool.of(scheduler.get()).ifPresent(ScheduledExecutorService::shutdown);
        }

        /**
         * @param event Event
         */
        public static void trigger(String event) {
            try (Lazy<Db> db = new Lazy<>(Db::connect)) {
                eventMap.getOrDefault(event, Collections.emptyList()).forEach(method -> {
                    Reflector.invoke(method, Stream.of(method.getParameters()).map(Parameter::getType).map(type -> {
                        if (Application.class.isAssignableFrom(type)) {
                            return Application.current().orElse(null);
                        }
                        if (Session.class.isAssignableFrom(type)) {
                            return Session.current().orElse(null);
                        }
                        if (Request.class.isAssignableFrom(type)) {
                            return Request.current().orElse(null);
                        }
                        if (Db.class.isAssignableFrom(type)) {
                            return db.get();
                        }
                        return null;
                    }).toArray());
                });
            }
        }
    }
}
