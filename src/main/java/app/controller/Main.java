package app.controller;

import java.util.Optional;

import app.config.Sys;
import framework.Application;
import framework.Response;
import framework.Session;
import framework.Tool;
import framework.annotation.Letters;
import framework.annotation.Route;
import framework.annotation.Size;

/**
 * main controller
 */
public class Main {

    /**
     * @param application Application
     * @param session Session
     * @param extension Extension
     * @param loginId Login id
     * @param password Password
     * @param url redirect url
     * @return Response
     */
    @Route(value = "login(?<extension>\\.json|)")
    Object login(Application application, Session session, String extension,
            @Size(min = 4, value = 20) @Letters(Letters.ASCII) Optional<String> loginId, @Letters(Letters.ASCII) Optional<String> password, Optional<String> url) {
        boolean isJson = ".json".equals(extension);
        if (session.login(loginId.orElse("guest"), password.orElse(""))) {
            session.remove("alert");
            if (isJson) {
                return Response.of(Tool.array("", url.orElseGet(application::getContextPath)));
            }
            return Response.redirect(url.orElseGet(application::getContextPath));
        } else {
            if (isJson) {
                return Response.of(Tool.array(Sys.Alert.loginFailed, Tool.map("loginId", null, "password", null)));
            }
            session.setAttr("alert", Sys.Alert.loginFailed);
            return Response.redirect(Tool.path(application.getContextPath(), Sys.redirect_if_not_login.orElse("")).apply("/"));
        }
    }

    /**
     * @param application Application
     * @param extension Extension
     * @param session Session
     * @param url redirect url
     * @return Response
     */
    @Route(value = "logout(?<extension>\\.json|)")
    Object logout(Application application, String extension, Session session, Optional<String> url) {
        session.logout();
        session.put("alert", "");
        return ".json".equals(extension) ? Response.of(Tool.array("", url.orElseGet(application::getContextPath))) : Response.redirect(url.orElseGet(application::getContextPath));
    }

    /**
     * @param session Session
     * @return Response
     */
    @Route("alert")
    Object alert(Session session) {
        return session.flash("alert");
    }
}
