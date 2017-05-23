package app.controller;

import java.util.Optional;

import app.config.Sys;
import framework.Application;
import framework.Response;
import framework.Session;
import framework.annotation.Route;
import framework.annotation.Route.Method;

/**
 * main controller
 */
public class Main {

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
            session.setAttr("alert", Sys.Alert.loginFailed);
            return Response.redirect(Sys.redirect_if_not_login.orElse(Application.current().get().getContextPath()));
        }
    }

    /**
     * @param session session
     * @return response
     */
    @Route
    Object logout(Session session) {
        session.logout();
        return Response.redirect("index.html");
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
