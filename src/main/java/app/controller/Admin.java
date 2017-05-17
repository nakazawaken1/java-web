package app.controller;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import app.config.Sys;
import app.model.Account;
import app.model.Person;
import framework.Db;
import framework.Diff;
import framework.Request;
import framework.Response;
import framework.Response.Render;
import framework.Response.Status;
import framework.Session;
import framework.Tool;
import framework.Try;
import framework.Xml;
import framework.annotation.Accept;
import framework.annotation.Config;
import framework.annotation.Content;
import framework.annotation.Letters;
import framework.annotation.Only;
import framework.annotation.Only.Administrator;
import framework.annotation.Route;
import framework.annotation.Valid;
import framework.annotation.Valid.Read;

/**
 * main controller
 */
@Route
@Only(Administrator.class)
public class Admin {

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
    Object sql(Db db, Optional<String> sql) throws SQLException {
        return Response.Template.of("table.html", (out, name, prefix) -> {
            if (!"".equals(name)) {
                return;
            }
            AtomicInteger columns = new AtomicInteger(-1);
            long rows = db.query(sql.orElseGet(() -> db.getSQL("system.variables.sql").get()), null).peek(Try.c(rs -> {
                if (columns.compareAndSet(-1, 0)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    columns.set(meta.getColumnCount());
                    out.println(Xml.of("tr").child("th", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(meta::getColumnName))));
                }
                out.println(Xml.of("tr").child("td", IntStream.rangeClosed(1, columns.get()).mapToObj(Try.intF(rs::getString))));
            })).count();
            out.printf("<caption>%d rows %d columns</caption>", rows, columns.get());
        });
    }

    /**
     * @return response
     */
    @Route
    Object db() {
        return Sys.h2_port.map(port -> Response.redirect("http://localhost:" + port)).orElseGet(() -> Response.error(Status.Not_Found));
    }

    /**
     * default config file
     * 
     * @return response
     */
    @Route
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

    /**
     * @param session session
     * @param request request
     * @param before before
     * @param after after
     * @return response
     */
    @Route(extensions = ".html")
    Object diff2(Session session, Request request, Optional<String> before, Optional<String> after) {
        boolean isFull = request.getParameters().containsKey("full");
        if (isFull || request.getParameters().containsKey("compact")) {
            before = session.getAttr("before");
            after = session.getAttr("after");
        }
        Optional<String> after2 = after;
        return before.flatMap(b -> after2.<Object>map(a -> {
            session.put("before", b);
            session.put("after", a);
            List<Diff<String>> list = Diff.compact(Diff.diff(b.split("\r?\n"), a.split("\r?\n"), Diff.IGNORE_SPACE, Diff.INLINE("b", 2).andThen(Diff.TAB(4))),
                    isFull ? 0 : 3, Sys.Item.reader.toString());
            return Render.of("diff2.html",
                    xml -> xml.attr("name", isFull ? "compact" : "full").attr("value", isFull ? Sys.Item.compact.toString() : Sys.Item.full.toString()),
                    xml -> xml.repeat(list.stream(), (x, d) -> {
                        x.attr("class", d.type.toString());
                        x.children().get(0).text(d.getBeforeIndexText());
                        x.children().get(1).text(d.getBeforeText());
                        x.children().get(2).text(d.getAfterIndexText());
                        x.children().get(3).text(d.getAfterText());
                        return x;
                    }));
        })).orElseGet(() -> Response.file("diff2.html"));
    }

    /**
     * @param db db
     * @param account condition
     * @return response
     */
    @Route
    @Accept(Accept.FORM)
    @Content({ Content.JSON, Content.HTML, Content.TEXT, Content.XML, Content.CSV, Content.TSV })
    Object accounts(Db db, @Valid(Read.class) Optional<Account> account) {
        return db.find(Account.class);
    }

    /**
     * @param db db
     * @return response
     */
    @Route(extensions = {})
    Object persons(Db db) {
        return db.find(Person.class).map(p -> Tool.map("id", p.id, "name", p.name, "age", p.getAge().map(Object::toString).orElse("不明")));
    }
}
