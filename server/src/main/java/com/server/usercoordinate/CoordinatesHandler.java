package com.server.usercoordinate;

import com.server.CoordinateDatabase;
import com.server.usercoordinate.UserCoordinate.WeatherException;
import com.sun.net.httpserver.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;

import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

public class CoordinatesHandler implements HttpHandler {

    public CoordinatesHandler() {
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        if (httpExchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePostRequest(httpExchange);
        } else if (httpExchange.getRequestMethod().equalsIgnoreCase("GET")) {
            buildGetResponse(httpExchange);
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
                        if (requestJson.has("query")) {
                            JSONObject responseJson = queryCoordinates(requestJson);
                            responseCode = responseJson.getInt("responsecode");
                            responseMessage = responseJson.getString("responsemessage");
                        } else if (isValidUserCoordinate(requestJson)) {
                            JSONObject responseJson = new UserCoordinate(requestJson).addUserCoordinateToDatabase();
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
            handleResponse(httpExchange, 500, "COULDN'T ADD COORDINATE TO DATABASE\n");
            e.printStackTrace();
        } catch (WeatherException e) {
            handleResponse(httpExchange, 400, "COULDN'T ADD WEATHER TO COORDINATE\n");
            e.printStackTrace();
        } catch (Exception e) {
            handleResponse(httpExchange, 500, "UNEXPECTED ERROR HAS OCCURED IN POSTING COORDINATES\n");
            e.printStackTrace();
        }
    }

    private void buildGetResponse(HttpExchange httpExchange) throws IOException {
        int responseCode = 200;
        String responseMessage = "";
        try {
            JSONArray arr = CoordinateDatabase.getInstance().getCoordinates();
            if (!arr.isEmpty()) {
                httpExchange.getResponseHeaders().add("Content-Type", "application/json");
                responseCode = 200;
                responseMessage = arr.toString();
                handleResponse(httpExchange, responseCode, responseMessage);
            } else {
                responseCode = 204;
                httpExchange.sendResponseHeaders(responseCode, -1);
            }
        } catch (SQLException e) {
            handleResponse(httpExchange, 500, "SOMETHING WENT WRONG WHEN READING COORDINATES");
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

    private boolean isValidUserCoordinate(JSONObject obj) throws JSONException {
        obj.getDouble("latitude");
        obj.getDouble("longitude");
        if (obj.has("description")) {
            return obj.getString("username").length() > 0 && obj.getString("sent").length() > 0
                    && obj.getString("description").length() <= 1024;
        }
        return obj.getString("username").length() > 0 && obj.getString("sent").length() > 0;
    }

    private JSONObject queryCoordinates(JSONObject obj) throws JSONException, SQLException {
        int responseCode = 400;
        String responseMessage = "";
        JSONObject responseJson = new JSONObject();
        if (obj.getString("query").equals("user")) {
            JSONArray dataFromQuery = CoordinateDatabase.getInstance()
                    .getCoordinatesByNickname(obj.getString("nickname"));
            if (!dataFromQuery.isEmpty()) {
                responseCode = 200;
                responseMessage = dataFromQuery.toString();
            } else {
                responseJson.put("responsecode", 204);
                responseJson.put("responsemessage", -1);
                return responseJson;
            }
        }
        if (obj.getString("query").equals("time")) {
            JSONArray dataFromQuery = CoordinateDatabase.getInstance().getCoordinatesByTime(obj.getString("timestart"),
                    obj.getString("timeend"));
            if (!dataFromQuery.isEmpty()) {
                responseCode = 200;
                responseMessage = dataFromQuery.toString();
            } else {
                responseJson.put("responsecode", 204);
                responseJson.put("responsemessage", -1);
                return responseJson;
            }
        }
        responseJson.put("responsecode", responseCode);
        responseJson.put("responsemessage", responseMessage);
        return responseJson;
    }
}
