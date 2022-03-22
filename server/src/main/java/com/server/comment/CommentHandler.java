package com.server.comment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

import com.sun.net.httpserver.*;

import org.json.JSONException;
import org.json.JSONObject;

public class CommentHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePostRequest(httpExchange);
        } else {
            handleResponse(httpExchange, 405, "NOT SUPPORTED\n");
        }
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
                        if (isValidComment(requestJson)) {
                            JSONObject responseJson = new Comment(requestJson).addCommentToDatabase();
                            responseCode = responseJson.getInt("responsecode");
                            responseMessage = responseJson.getString("responsemessage");
                        } else {
                            responseCode = 400;
                            responseMessage = "BAD REQUEST\n";
                        }
                    } else {
                        responseCode = 400;
                        responseMessage = "NO DATA\n";
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
        } catch (DateTimeParseException e) {
            handleResponse(httpExchange, 400, "BAD DATE-TIME STRING PROVIDED IN JSON\n");
            e.printStackTrace();
        } catch (ArithmeticException e) {
            handleResponse(httpExchange, 500, "DATE-TIME OVERFLOW\n");
            e.printStackTrace();
        } catch (SQLException e) {
            handleResponse(httpExchange, 500, "COULDN'T ADD COMMENT TO DATABASE\n");
            e.printStackTrace();
        } catch (Exception e) {
            handleResponse(httpExchange, 500, "UNEXPECTED ERROR HAS OCCURED IN POSTING COORDINATES\n");
            e.printStackTrace();
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

    private String readRequestBody(HttpExchange httpExchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(httpExchange.getRequestBody(), StandardCharsets.UTF_8))) {

            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private boolean isValidComment(JSONObject obj) throws JSONException {
        return obj.getInt("id") > 0 && obj.getString("comment").length() > 0 && obj.getString("sent").length() > 0;
    }
}
