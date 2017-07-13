package app.controller;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import app.config.Sys;
import app.model.Account;
import framework.Application;
import framework.Db;
import framework.Diff;
import framework.Request;
import framework.Response;
import framework.Response.Render;
import framework.Response.Status;
import framework.Session;
import framework.Tool;
import framework.Tool.XmlTraverser;
import framework.Try;
import framework.Xml;
import framework.annotation.Accept;
import framework.annotation.Config;
import framework.annotation.Letters;
import framework.annotation.Only;
import framework.annotation.Only.Administrator;
import framework.annotation.Route;

/**
 * main controller
 */
@Route("admin")
@Only(Administrator.class)
public class Admin {

    /**
     * top page
     * 
     * @param session session
     * @param request request
     * @return response
     */
    @Route(value = "index[.]html")
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
    @Route("sql")
    Object sql(Db db, Optional<String> sql) throws SQLException {
        return Response.Template.of("admin/table.html", (out, name, prefix) -> {
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
    @Route("db")
    Object db() {
        return Sys.h2_web_port.map(port -> Response.redirect("http://localhost:" + port)).orElseGet(() -> Response.error(Status.Not_Found));
    }

    /**
     * Config
     * 
     * @return response
     */
    @Route("config")
    Object config() {
        return diff(Session.current().get(), Request.current().get(), Optional.of(Config.Injector.getDefault(Sys.class)),
                Optional.of(String.join(Letters.CRLF, Config.Injector.dumpConfig(Sys.class, true)))).bind("before", "初期設定").bind("after", "現在の設定")
                        .bind("breadcrumb", Tool.list("<a href=\"index.html\">" + Sys.Item.adminTitle + "</a>", Sys.Item.config));
    }

    /**
     * Route
     * 
     * @return response
     */
    @Route("route")
    Object route() {
        return Response
                .of(Response.Template.of("admin/table.html",
                        (out, name,
                                prefix) -> out.println(Xml.of(null).child("tr").child("th", Stream.of("Method", "Path", "Action", "Parameter map")).parent()
                                        .get().child(Xml.of("tr").repeat(Application.routes(), (xml, a) -> xml.child("td", Stream.of(a)))))))
                .bind("breadcrumb", Tool.list("<a href=\"index.html\">" + Sys.Item.adminTitle + "</a>", Sys.Item.route));
    }

    /**
     * EL version
     * 
     * @param session session
     * @param request request
     * @param before before
     * @param after after
     * @return response
     */
    @Route("diff[.]html")
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
            return Response.template("admin/diff.html").bind("isFull", isFull).bind("diffs",
                    Diff.compact(Diff.diff(b.split("\r?\n"), a.split("\r?\n"), Diff.IGNORE_SPACE, Diff.INLINE("b", 2).andThen(Diff.TAB(4))), isFull ? 0 : 3,
                            Sys.Item.reader.toString()));
        })).orElseGet(() -> Response.file("admin/diff.html"));
    }

    /**
     * Render version
     * 
     * @param session session
     * @param request request
     * @param before before
     * @param after after
     * @return response
     */
    @Route("diff2[.]html")
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
            return Render.of("admin/diff2.html",
                    xml -> xml.attr("name", isFull ? "compact" : "full").attr("value", isFull ? Sys.Item.compact.toString() : Sys.Item.full.toString()),
                    xml -> xml.repeat(list.stream(), (x, d) -> {
                        x.attr("class", d.type.toString());
                        x.children().get(0).innerHtml(d.getBeforeIndexText());
                        x.children().get(1).innerHtml(d.getBeforeText());
                        x.children().get(2).innerHtml(d.getAfterIndexText());
                        x.children().get(3).innerHtml(d.getAfterText());
                        return x;
                    }));
        })).orElseGet(() -> Response.file("admin/diff2.html"));
    }

    /**
     * @param db db
     * @return response
     */
    @Route("accounts[.]?(|json|html|txt|xml|csv|tsv)")
    @Accept(Accept.FORM)
    Object accounts(Db db) {
        Object o;
        if (Sys.login_method.endsWith("loginWithConfig")) {
            o = Sys.accounts.stream().map(Tool.bindRight(String::split, "[:]")).map(array -> Account.fromArray(array[0]).apply(array));
        } else {
            o = db.find(Account.class);
        }
        return Response.of(o).traverser(XmlTraverser.class, t -> t.classMap.put("Object", "アカウント一覧"));
    }
}
