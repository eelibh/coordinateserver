package com.server.comment;

import java.sql.SQLException;
import java.time.format.DateTimeParseException;

import com.server.CoordinateDatabase;

import org.json.JSONObject;

public class Comment {

    private int id;
    private String comment;
    private String sent;

    public Comment(JSONObject obj) {
        setId(obj.getInt("id"));
        setComment(obj.getString("comment"));
        setSent(obj.getString("sent"));
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSent() {
        return sent;
    }

    public void setSent(String sent) {
        this.sent = sent;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public JSONObject addCommentToDatabase() throws ArithmeticException, SQLException, DateTimeParseException {
        int responseCode = 200;
        String responseMessage = "";
        if (CoordinateDatabase.getInstance().addComment(this)) {
            responseCode = 200;
            responseMessage = "Comment added succesfully!\n";
        } else {
            responseCode = 400;
            responseMessage = "Failed to add comment";
        }
        JSONObject responseJson = new JSONObject();
        responseJson.put("responsecode", responseCode);
        responseJson.put("responsemessage", responseMessage);
        return responseJson;
    }
}
