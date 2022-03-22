package com.server;

import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.io.File;
import java.util.Base64;

import com.server.comment.Comment;
import com.server.usercoordinate.UserCoordinate;

import java.security.SecureRandom;
import org.apache.commons.codec.digest.Crypt;

public class CoordinateDatabase {

    private Connection dbConnection = null;
    private static CoordinateDatabase dbInstance = null;
    SecureRandom secureRandom = new SecureRandom();

    public static synchronized CoordinateDatabase getInstance() {
        if (dbInstance == null) {
            dbInstance = new CoordinateDatabase();
        }
        return dbInstance;
    }

    private CoordinateDatabase() {
        try {
            open("CoordinateDB.db");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean init() throws SQLException {
        boolean dbCreated = false;
        if (dbConnection != null) {
            try (Statement st = dbConnection.createStatement()) {
                st.executeUpdate(
                        "CREATE TABLE USERS (USERNAME VARCHAR(50) NOT NULL PRIMARY KEY, PASSWORD VARCHAR(50) NOT NULL, SALT VARCHAR(50), EMAIL VARCHAR(50) NOT NULL)");
                st.executeUpdate(
                        "CREATE TABLE COORDINATES (ID INTEGER PRIMARY KEY, USERNAME VARCHAR(50) NOT NULL, LATITUDE REAL NOT NULL, LONGITUDE REAL NOT NULL, SENT DATETIME NOT NULL, DESCRIPTION VARCHAR(1024), WEATHER VARCHAR(50))");
                // foreign keys are not enabled
                st.executeUpdate(
                        "CREATE TABLE COMMENTS (COMMENT VARCHAR(256), SENT DATETIME NOT NULL, ID INTEGER)");
                dbCreated = true;
            }
        }
        return dbCreated;
    }

    public void open(String dbName) throws SQLException {
        File file = new File(dbName);
        boolean fileExists = file.exists();
        String connectionAddress = "jdbc:sqlite:" + dbName;
        dbConnection = DriverManager.getConnection(connectionAddress);
        if (!fileExists) {
            init();
        }
    }

    public boolean addUser(JSONObject user) throws SQLException {
        boolean userAdded = false;
        if (!getUserByUsername(user.getString("username"))) {
            byte bytes[] = new byte[13];
            secureRandom.nextBytes(bytes);
            String saltBytes = new String(Base64.getEncoder().encode(bytes));
            String salt = "$6$" + saltBytes;
            String hashedPassword = Crypt.crypt(user.getString("password"), salt);
            try (PreparedStatement ps = dbConnection.prepareStatement("INSERT INTO USERS VALUES (?,?,?,?)")) {
                ps.setString(1, user.getString("username"));
                ps.setString(2, hashedPassword);
                ps.setString(3, salt);
                ps.setString(4, user.getString("email"));
                ps.executeUpdate();
                userAdded = true;
            }
        }
        return userAdded;
    }

    public boolean getUserByUsername(String username) throws SQLException {
        boolean userFound = false;
        try (PreparedStatement ps = dbConnection.prepareStatement("SELECT USERNAME FROM USERS WHERE USERNAME = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                userFound = true;
            }
        }
        return userFound;
    }

    public boolean authenticateUser(String username, String password) throws SQLException {
        Boolean userAuthenticated = false;
        try (PreparedStatement ps = dbConnection
                .prepareStatement("SELECT USERNAME, PASSWORD FROM USERS WHERE USERNAME = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getString("PASSWORD").equals(Crypt.crypt(password, rs.getString("PASSWORD")))) {
                userAuthenticated = true;
            }
        }
        return userAuthenticated;
    }

    public boolean addCoordinate(UserCoordinate userCoordinate)
            throws SQLException, ArithmeticException, DateTimeParseException {
        boolean coordinateAdded = false;
        try (PreparedStatement ps = dbConnection.prepareStatement("INSERT INTO COORDINATES VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(2, userCoordinate.getNickname());
            ps.setDouble(3, userCoordinate.getLatitude());
            ps.setDouble(4, userCoordinate.getLongitude());
            ps.setLong(5, dateAsInt(OffsetDateTime.parse(userCoordinate.getSent()).toLocalDateTime()));
            if (userCoordinate.getDescription() != null) {
                ps.setString(6, userCoordinate.getDescription());
            }
            if (userCoordinate.getWeather() != null) {
                ps.setString(7, userCoordinate.getWeather());
            }
            ps.executeUpdate();
            coordinateAdded = true;
        }
        return coordinateAdded;
    }

    public JSONArray getCoordinates() throws SQLException {
        JSONArray arr = new JSONArray();
        try (PreparedStatement ps = dbConnection.prepareStatement("SELECT * FROM COORDINATES")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("id", rs.getInt(1));
                obj.put("username", rs.getString(2));
                obj.put("latitude", rs.getDouble(3));
                obj.put("longitude", rs.getDouble(4));
                obj.put("sent", setSent(rs.getLong(5)).toString());
                if (rs.getString(6) != null) {
                    obj.put("description", rs.getString(6));
                }
                if (rs.getString(7) != null) {
                    obj.put("weather", rs.getString(7));
                }
                JSONArray comments = getCommentsByID(rs.getInt(1));
                if (comments.length() > 0) {
                    obj.put("comments", comments);
                }
                arr.put(obj);
            }
        }
        return arr;
    }

    public JSONArray getCoordinatesByNickname(String username) throws SQLException {
        JSONArray arr = new JSONArray();
        try (PreparedStatement ps = dbConnection.prepareStatement("SELECT * FROM COORDINATES WHERE USERNAME = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("id", rs.getInt(1));
                obj.put("username", rs.getString(2));
                obj.put("latitude", rs.getDouble(3));
                obj.put("longitude", rs.getDouble(4));
                obj.put("sent", setSent(rs.getLong(5)).toString());
                if (rs.getString(6) != null) {
                    obj.put("description", rs.getString(6));
                }
                if (rs.getString(7) != null) {
                    obj.put("weather", rs.getString(7));
                }
                JSONArray comments = getCommentsByID(rs.getInt(1));
                if (comments.length() > 0) {
                    obj.put("comments", comments);
                }
                arr.put(obj);
            }
        }
        return arr;
    }

    public JSONArray getCoordinatesByTime(String begin, String end) throws SQLException {
        JSONArray arr = new JSONArray();
        try (PreparedStatement ps = dbConnection
                .prepareStatement("SELECT * FROM COORDINATES WHERE SENT BETWEEN ? AND ?")) {
            ps.setLong(1, dateAsInt(OffsetDateTime.parse(begin).toLocalDateTime()));
            ps.setLong(2, dateAsInt(OffsetDateTime.parse(end).toLocalDateTime()));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("id", rs.getInt(1));
                obj.put("username", rs.getString(2));
                obj.put("latitude", rs.getDouble(3));
                obj.put("longitude", rs.getDouble(4));
                obj.put("sent", setSent(rs.getLong(5)).toString());
                if (rs.getString(6) != null) {
                    obj.put("description", rs.getString(6));
                }
                if (rs.getString(7) != null) {
                    obj.put("weather", rs.getString(7));
                }
                JSONArray comments = getCommentsByID(rs.getInt(1));
                if (comments.length() > 0) {
                    obj.put("comments", comments);
                }
                arr.put(obj);
            }
        }
        return arr;
    }

    public boolean addComment(Comment comment) throws ArithmeticException, SQLException, DateTimeParseException {
        boolean commentAdded = false;
        try (PreparedStatement ps = dbConnection.prepareStatement("INSERT INTO COMMENTS VALUES (?,?,?)")) {
            ps.setString(1, comment.getComment());
            ps.setLong(2, dateAsInt(OffsetDateTime.parse(comment.getSent()).toLocalDateTime()));
            ps.setInt(3, comment.getId());
            ps.executeUpdate();
            commentAdded = true;
        }
        return commentAdded;
    }

    public JSONArray getCommentsByID(int id) throws SQLException {
        JSONArray arr = new JSONArray();
        try (PreparedStatement ps = dbConnection.prepareStatement("SELECT * FROM COMMENTS WHERE ID = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("comment", rs.getString(1));
                obj.put("sent", setSent(rs.getLong(2)).toString());
                obj.put("id", rs.getInt(3));
                arr.put(obj);
            }
        }
        return arr;
    }

    public void close() throws SQLException {
        if (dbConnection != null) {
            dbConnection.close();
        }
    }

    public long dateAsInt(LocalDateTime sent) throws ArithmeticException {
        return sent.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public ZonedDateTime setSent(long epoch) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
    }
}
