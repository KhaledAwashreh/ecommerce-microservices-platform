package com.kawashreh.ecommerce.frontend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class SessionManager {

    public static final String JWT_TOKEN = "jwt_token";
    public static final String USER_ID = "user_id";
    public static final String USERNAME = "username";

    public void storeToken(HttpServletRequest request, String token, String username) {
        HttpSession session = request.getSession();
        session.setAttribute(JWT_TOKEN, token);
        session.setAttribute(USERNAME, username);
    }

    public String getToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        return (String) session.getAttribute(JWT_TOKEN);
    }

    public String getUsername(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        return (String) session.getAttribute(USERNAME);
    }

    public void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public boolean isAuthenticated(HttpServletRequest request) {
        return getToken(request) != null;
    }
}
