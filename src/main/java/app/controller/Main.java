package app.controller;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import app.model.Person;
import framework.Config;
import framework.Db;
import framework.Response;
import framework.Session;
import framework.Try;
import framework.Tuple;
import framework.Xml;
import framework.annotation.Http;
import framework.annotation.Http.Method;
import framework.annotation.Job;
import framework.annotation.Only;
import framework.annotation.Only.Administrator;
import framework.annotation.Query;
import framework.annotation.Valid;
import framework.annotation.Valid.Delete;
import framework.annotation.Valid.Read;
import framework.annotation.Valid.Save;

/**
 * main controller
 */
public class Main {
    
    /**
     * top page
     * @param session session
     * @return response
     */
    @Http(path="index.html")
    Response index(Session session) {
        return Response.file(session.isLoggedIn() ? "index.html" : "login.html");
    }

    /**
     * @param db db
     * @param sql sql
     * @return response
     * @throws SQLException database error
     */
    @Http
    @Only(Administrator.class)
    Response db(Db db, @Query Optional<String> sql) throws SQLException {
        return Response.writeTemplate("table.html", (out, name, prefix) -> {
            if(!"".equals(name)) {
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
        if (session.isLoggedIn()) {
            return Response.template("logged_in.html");
        } else {
            return Response.text("");
        }
    }

    /**
     * @param session session
     * @param loginId login id
     * @param password password
     * @return response
     */
    @Http(Method.POST)
    Response login(Session session, @Query Optional<String> loginId, @Query Optional<String> password) {
        if (session.login(loginId.orElse("guest"), password.orElse(""))) {
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
    @Http
    Response logout(Session session) {
        session.logout();
        return Response.redirect("login.html");
    }

    /**
     * @param session session
     * @return response
     */
    @Http
    Response alert(Session session) {
        return Response.text(session.flash("alert"));
    }

    /**
     * search records
     * 
     * @param db db
     * @param person condition
     * @return response
     */
    @Http
    Response find(Db db, @Valid(Read.class) Person person) {
        return Response.json(db.find(person));
    }

    /**
     * create or update a new record
     * 
     * @param db db
     * @param person save data
     * @return response
     */
    @Http
    Response save(Db db, @Valid(Save.class) Person person) {
        db.save(person);
        return Response.json(Tuple.of("id", person.getId()));
    }

    /**
     * delete a record
     * 
     * @param db db
     * @param person delete key
     * @return response
     */
    @Http
    Response delete(Db db, @Valid(Delete.class) Person person) {
        db.delete(person);
        return Response.json(Tuple.of("id", person.getId()));
    }

    /**
     * daily job
     */
    @Job("job.daily")
    void daily() {
        Logger.getGlobal().info("daily");
    }
    
    /**
     * default config file
     * @return response
     */
    @Http
    @Only(Administrator.class)
    Response config() {
        return Response.write(Config::createFile);
    }
}
