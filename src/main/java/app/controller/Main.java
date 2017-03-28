package app.controller;

import java.nio.file.Paths;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import framework.Config;
import framework.Db;
import framework.Request;
import framework.Response;
import framework.Session;
import framework.Try;
import framework.Xml;
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
    @Route
    Object index(Session session, Request request) {
        if (!session.isLoggedIn()) {
            return Response.redirect("login.html");
        }
        return Paths.get(request.getPath());
    }

    /**
     * @param db db
     * @param sql sql
     * @return response
     * @throws SQLException database error
     */
    @Route
    @Only(Administrator.class)
    Object db_settings(Db db, Optional<String> sql) throws SQLException {
        return new Response.Template("table.html", (out, name, prefix) -> {
            if (!"".equals(name)) {
                return;
            }
            AtomicInteger columns = new AtomicInteger(-1);
            db.query(sql.orElse(db.getBuilder().getVariablesSql()), null).forEach(Try.c(rs -> {
                if (columns.compareAndSet(-1, 0)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    columns.set(meta.getColumnCount());
                    out.println(new Xml("tr").child("th", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(meta::getColumnName))));
                }
                out.println(new Xml("tr").child("td", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(rs::getString))));
            }));
        });
    }

    /**
     * @return response
     */
    @Route
    @Only(Administrator.class)
    Object db_console() {
        return Response.redirect("http://localhost:" + Config.app_h2_port.integer());
    }

    /**
     * @param a left term
     * @param b right term
     * @return response
     */
    @Route
    @Only
    Object add(int a, Optional<Integer> b) {
        return Response.write(out -> out.println(a + " + " + b + " = " + (a + b.orElse(0))));
    }

    /**
     * @param session session
     * @return response
     */
    @Route
    Object info(Session session) {
        if (session.isLoggedIn()) {
            return Response.file("/template/logged_in.html");
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
    @Route(Method.POST)
    Object login(Session session, Optional<String> loginId, Optional<String> password) {
        if (session.login(loginId.orElse("guest"), password.orElse(""))) {
            session.remove("alert");
            return Response.redirect("index.html");
        } else {
            session.setAttr("alert", "ログインIDまたはパスワードが違います。");
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
    Object config_default() {
        return Response.write(Config::printDefault).contentType("text/plain;charset=UTF-8");
    }

    /**
     * default config file
     * 
     * @return response
     */
    @Route
    @Only(Administrator.class)
    Object config_current() {
        return Response.write(Config::printCurrent).contentType("text/plain;charset=UTF-8");
    }
}
