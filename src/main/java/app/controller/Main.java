package app.controller;

import java.util.Optional;

import app.config.Sys;
import framework.Request;
import framework.Response;
import framework.Session;
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
}
