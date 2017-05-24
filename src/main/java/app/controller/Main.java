package app.controller;

import java.util.Optional;

import app.config.Sys;
import framework.Application;
import framework.Response;
import framework.Session;
import framework.Tool;
import framework.annotation.Route;
import framework.annotation.Route.Method;

/**
 * main controller
 */
public class Main {

    /**
     * @param application Application
     * @param session Session
     * @param loginId Login id
     * @param password Password
     * @return Response
     */
    @Route(value = Method.POST)
    Object login(Application application, Session session, Optional<String> loginId, Optional<String> password) {
        if (session.login(loginId.orElse("guest"), password.orElse(""))) {
            session.remove("alert");
            return Response.redirect(application.getContextPath());
        } else {
            session.setAttr("alert", Sys.Alert.loginFailed);
            return Response.redirect(Tool.path(application.getContextPath(), Sys.redirect_if_not_login.orElse("")).apply("/"));
        }
    }

    /**
     * @param application Application
     * @param session Session
     * @return Response
     */
    @Route
    Object logout(Application application, Session session) {
        session.logout();
        return Response.redirect(application.getContextPath());
    }

    /**
     * @param session Session
     * @return Response
     */
    @Route
    Object alert(Session session) {
        return session.flash("alert");
    }
}
