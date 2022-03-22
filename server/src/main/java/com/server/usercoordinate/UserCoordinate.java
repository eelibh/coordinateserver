package com.server.usercoordinate;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.parsers.DocumentBuilderFactory;

import com.server.CoordinateDatabase;

import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.sql.SQLException;
import java.time.format.DateTimeParseException;

public class UserCoordinate {

    private String nickname;
    private double latitude;
    private double longitude;
    private String sent;
    private String description;
    private String weather;

    public UserCoordinate(JSONObject userCoordinate) throws WeatherException {
        setNickname(userCoordinate.getString("username"));
        setLatitude(userCoordinate.getDouble("latitude"));
        setLongitude(userCoordinate.getDouble("longitude"));
        setSent(userCoordinate.getString("sent"));
        if (userCoordinate.has("description")) {
            setDescription(userCoordinate.getString("description"));
        }
        if (userCoordinate.has("weather")) {
            setWeather();
        }
    }

    public String getWeather() {
        return weather;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSent() {
        return sent;
    }

    public void setSent(String sent) {
        this.sent = sent;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    private void setWeather() throws WeatherException {
        try {
            byte[] request = buildCoordinatesXML().getBytes("UTF-8");
            HttpURLConnection con = createConnection(new URL("https://localhost:4001/weather"));
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/xml");
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestProperty("Content-Length", String.valueOf(request.length));
            try (OutputStream out = con.getOutputStream()) {
                out.write(request);
                out.flush();
            }
            try (InputStream in = con.getInputStream()) {
                String str = new String(in.readAllBytes());
                Document response = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new InputSource(new StringReader(new String(str))));
                this.weather = response.getElementsByTagName("temperature").item(0).getTextContent();
            }
        } catch (Exception e) {
            throw new WeatherException(e);
        }

    }

    private String buildCoordinatesXML() {
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<coordinates>")
                .append("<latitude>")
                .append(getLatitude())
                .append("</latitude>")
                .append("<longitude>")
                .append(getLongitude())
                .append("</longitude>")
                .append("</coordinates>");
        return xmlBuilder.toString();
    }

    public JSONObject addUserCoordinateToDatabase() throws SQLException, ArithmeticException, DateTimeParseException {
        int responseCode = 200;
        String responseMessage = "";
        if (CoordinateDatabase.getInstance().addCoordinate(this)) {
            responseCode = 200;
            responseMessage = "Coordinate added succesfully!\n";
        } else {
            responseCode = 400;
            responseMessage = "Failed to add coordinate";
        }
        JSONObject responseJson = new JSONObject();
        responseJson.put("responsecode", responseCode);
        responseJson.put("responsemessage", responseMessage);
        return responseJson;
    }

    /**
     * <p>
     * Creates a trusting connection to a server with a specific self-signed
     * certificate.
     * </p>
     * 
     * @param url The URL address to which to connect to.
     * @return The connection as HttpURLConnection.
     * @throws CertificateException
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws KeyManagementException
     */
    private HttpURLConnection createConnection(URL url) throws CertificateException, KeyStoreException,
            NoSuchAlgorithmException, IOException, KeyManagementException {
        String keystore = ""; /* <--- CERT-LOCATION GOES HERE */
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new FileInputStream(keystore));
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(null, null);
        ks.setCertificateEntry("localhost", certificate);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, tmf.getTrustManagers(), null);
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setSSLSocketFactory(ssl.getSocketFactory());
        return con;
    }

    public class WeatherException extends Exception {
        public WeatherException(String message) {
            super(message);
        }

        public WeatherException(Throwable cause) {
            super(cause);
        }
    }
}
