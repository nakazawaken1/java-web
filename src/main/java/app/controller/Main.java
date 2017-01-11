package app.controller;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import app.model.Data;
import framework.Account;
import framework.Db;
import framework.Response;
import framework.Session;
import framework.Try;
import framework.Xml;
import framework.annotation.Http;
import framework.annotation.Only;
import framework.annotation.Only.Administrator;
import framework.annotation.Query;
import framework.annotation.Valid;
import framework.annotation.Valid.Delete;
import framework.annotation.Valid.Save;

/**
 * main controller
 */
public class Main {

    /**
     * @param sql sql
     * @return response
     * @throws SQLException database error
     */
    @Http
    @Only(Administrator.class)
    Response db(@Query Optional<String> sql) throws SQLException {
        return Response.template("table.html", (out, name) -> {
            try (Db db = Db.connect()) {
                AtomicInteger columns = new AtomicInteger(-1);
                db.query(sql.orElse("SHOW VARIABLES"), null).forEach(Try.c(rs -> {
                    if(columns.compareAndSet(-1, 0)) {
                        ResultSetMetaData meta = rs.getMetaData();
                        columns.set(meta.getColumnCount());
                        out.println(new Xml("tr").child("th", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(meta::getColumnName))));
                    }
                    out.println(new Xml("tr").child("td", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(rs::getString))));
                }));
            }
        });
    }

    /**
     * @param a left term
     * @param b right term
     * @return response
     */
    @Http
    @Only
    Response add(@Query int a, @Query Optional<Integer> b) {
        return Response.write(out -> out.println(a + " + " + b + " = " + (a + b.orElse(0))));
    }

    /**
     * @param session session
     * @return response
     */
    @Http
    Response info(Session session) {
        if (session.account().isPresent()) {
            return Response.template("logged_in.html", (out, name) -> out.print(session.account().orElse(Account.GUEST).name));
        } else {
            return Response.template("not_logged_in.html");
        }
    }

    /**
     * @param session session
     * @param loginId login id
     * @param password password
     * @return response
     */
    @Http
    Response login(Session session, @Query Optional<String> loginId, @Query Optional<String> password) {
        session.login(loginId.orElse("guest"), password.orElse(""));
        return Response.redirect("index.html");
    }

    /**
     * @param session session
     * @return response
     */
    @Http
    Response logout(Session session) {
        session.logout();
        return Response.redirect("index.html");
    }

    /**
     * @param session session
     * @return response
     */
    @Http
    Response flush(Session session) {
        return Response.write(out -> session.getAttr("flush").ifPresent(i -> {
            out.print(i);
            session.removeAttr("flush");
        }));
    }

    /**
     * create or update new record
     * 
     * @param data data
     * @return response
     */
    @Http
    Response save(@Valid(Save.class) Data data) {
        return Response.text("doing...");
    }

    /**
     * delete a record
     * 
     * @param data data
     * @return response
     */
    @Http
    Response delete(@Valid(Delete.class) Data data) {
        return Response.text("doing...");
    }
}
