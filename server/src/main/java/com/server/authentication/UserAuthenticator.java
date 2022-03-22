package com.server.authentication;

import com.server.CoordinateDatabase;
import com.sun.net.httpserver.BasicAuthenticator;

import java.sql.SQLException;

public class UserAuthenticator extends BasicAuthenticator {

    public UserAuthenticator(String realm) {
        super(realm);
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        try {
            return CoordinateDatabase.getInstance().authenticateUser(username, password);
        } catch (SQLException e) {
            return false;
        }
    }
}
