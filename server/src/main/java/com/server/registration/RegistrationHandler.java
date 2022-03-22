package com.server.registration;

import com.server.CoordinateDatabase;
import com.sun.net.httpserver.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.stream.Collectors;

public class RegistrationHandler implements HttpHandler {

    public RegistrationHandler() {
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePostRequest(httpExchange);
        } else
            handleResponse(httpExchange, 405, "NOT SUPPORTED");
    }

    private void handlePostRequest(HttpExchange httpExchange) throws IOException {
        int responseCode = 200;
        String responseMessage = "";
        try {
            if (httpExchange.getRequestHeaders().containsKey("Content-Type")) {
                String contentType = httpExchange.getRequestHeaders().get("Content-Type").get(0);
                if (contentType.equalsIgnoreCase("application/json")) {
                    String requestString = readRequestBody(httpExchange);
                    if (!requestString.isEmpty()) {
                        JSONObject requestJson = new JSONObject(requestString);
                        if (isValidUser(requestJson)) {

                            if (CoordinateDatabase.getInstance().addUser(requestJson)) {
                                responseCode = 200;
                                responseMessage = "User registered succesfully!\n";
                            } else {
                                responseCode = 400;
                                responseMessage = "USERNAME IS ALREADY TAKEN\n";
                            }
                        } else {
                            responseCode = 400;
                            responseMessage = "PROVIDE DATA FOR USERNAME, PASSWORD AND EMAIL\n";
                        }
                    } else {
                        responseCode = 400;
                        responseMessage = "PROVIDE DATA FOR USERNAME, PASSWORD AND EMAIL (EMPTY JSON)\n";
                    }
                } else {
                    responseCode = 400;
                    responseMessage = "CONTENT-TYPE IS NOT 'application/json'\n";
                }
            } else {
                responseCode = 400;
                responseMessage = "NO CONTENT TYPE IN REQUEST\n";
            }

            handleResponse(httpExchange, responseCode, responseMessage);

        } catch (JSONException e) {
            handleResponse(httpExchange, 400, "JSON SYNTAX ERROR\n");
            e.printStackTrace();
        } catch (SQLException e) {
            handleResponse(httpExchange, 500, "COULDN'T ADD USER TO DATABASE\n");
            e.printStackTrace();
        } catch (Exception e) {
            handleResponse(httpExchange, 500, "UNEXPECTED ERROR HAS OCCURED IN REGISTRATION");
            e.printStackTrace();
        }
    }

    private String readRequestBody(HttpExchange httpExchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8))) {

            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private void handleResponse(HttpExchange httpExchange, int responseCode, String responseMessage)
            throws IOException {
        byte[] bytes = responseMessage.getBytes("UTF-8");
        httpExchange.sendResponseHeaders(responseCode, bytes.length);
        OutputStream out = httpExchange.getResponseBody();
        out.write(bytes);
        out.flush();
        out.close();
    }

    private boolean isValidUser(JSONObject obj) {
        return obj.getString("username").length() > 0
                && obj.getString("password").length() > 0
                && obj.getString("email").length() > 0;
    }
}
