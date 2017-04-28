package app.controller;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import app.config.Sys;
import framework.Db;
import framework.Diff;
import framework.Request;
import framework.Response;
import framework.Response.Status;
import framework.Session;
import framework.Try;
import framework.Xml;
import framework.annotation.Config;
import framework.annotation.Letters;
import framework.annotation.Only;
import framework.annotation.Only.Administrator;
import framework.annotation.Route;
import framework.annotation.Route.Method;

/**
 * main controller
 */
public class Main {

    /**
     * top page
     * 
     * @param session session
     * @param request request
     * @return response
     */
    @Route(extensions = ".html")
    Object index(Session session, Request request) {
        if (!session.isLoggedIn()) {
            return Response.redirect("login.html");
        }
        return Response.file(request.getPath());
    }

    /**
     * @param db db
     * @param sql sql
     * @return response
     * @throws SQLException database error
     */
    @Route
    @Only(Administrator.class)
    Object sql(Db db, Optional<String> sql) throws SQLException {
        return new Response.Template("table.html", (out, name, prefix) -> {
            if (!"".equals(name)) {
                return;
            }
            AtomicInteger columns = new AtomicInteger(-1);
            long rows = db.query(sql.orElseGet(() -> db.getSQL("system.variables.sql").get()), null).peek(Try.c(rs -> {
                if (columns.compareAndSet(-1, 0)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    columns.set(meta.getColumnCount());
                    out.println(new Xml("tr").child("th", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(meta::getColumnName))));
                }
                out.println(new Xml("tr").child("td", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(rs::getString))));
            })).count();
            out.printf("<caption>%d rows %d columns</caption>", rows, columns.get());
        });
    }

    /**
     * @return response
     */
    @Route
    @Only(Administrator.class)
    Object db() {
        return Sys.h2_port.map(port -> Response.redirect("http://localhost:" + port)).orElseGet(() -> Response.error(Status.Not_Found));
    }

    /**
     * @param a left term
     * @param b right term
     * @return response
     */
    @Route
    @Only
    Object add(int a, Optional<Integer> b) {
        return a + " + " + b + " = " + (a + b.orElse(0));
    }

    /**
     * @param session session
     * @return response
     */
    @Route
    Object info(Session session) {
        if (session.isLoggedIn()) {
            return Response.template("logged_in.html");
        } else {
            return "";
        }
    }

    /**
     * @param session session
     * @param loginId login id
     * @param password password
     * @return response
     */
    @Route(value = Method.POST)
    Object login(Session session, Optional<String> loginId, Optional<String> password) {
        if (session.login(loginId.orElse("guest"), password.orElse(""))) {
            session.remove("alert");
            return Response.redirect("index.html");
        } else {
            session.setAttr("alert", Sys.Alert.login_failed);
            return Response.redirect("login.html");
        }
    }

    /**
     * @param session session
     * @return response
     */
    @Route
    Object logout(Session session) {
        session.logout();
        return Response.redirect("login.html");
    }

    /**
     * @param session session
     * @return response
     */
    @Route
    Object alert(Session session) {
        return session.flash("alert");
    }

    /**
     * default config file
     * 
     * @return response
     */
    @Route
    @Only(Administrator.class)
    Object config() {
        return diff(Session.current().get(), Request.current().get(), Optional.of(Config.Injector.getDefault(Sys.class)),
                Optional.of(String.join(Letters.CRLF, Config.Injector.dump(Sys.class, true)))).bind("before", "初期設定").bind("after", "現在の設定");
    }

    /**
     * @param session session
     * @param request request
     * @param before before
     * @param after after
     * @return response
     */
    @Route(extensions = ".html")
    Response diff(Session session, Request request, Optional<String> before, Optional<String> after) {
        boolean isFull = request.getParameters().containsKey("full");
        if (isFull || request.getParameters().containsKey("compact")) {
            before = session.getAttr("before");
            after = session.getAttr("after");
        }
        Optional<String> after2 = after;
        return before.flatMap(b -> after2.map(a -> {
            session.put("before", b);
            session.put("after", a);
            return Response.template("diff.html").bind("isFull", isFull).bind("diffs",
                    Diff.compact(Diff.diff(b.split("\r?\n"), a.split("\r?\n"), Diff.IGNORE_SPACE, Diff.INLINE("b", 2).andThen(Diff.TAB(4))), isFull ? 0 : 3,
                            Sys.Item.reader.toString()));
        })).orElseGet(() -> Response.file("diff.html"));
    }
}
