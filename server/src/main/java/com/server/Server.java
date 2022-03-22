package com.server;

import com.server.authentication.UserAuthenticator;
import com.server.comment.CommentHandler;
import com.server.registration.RegistrationHandler;
import com.server.usercoordinate.CoordinatesHandler;
import com.sun.net.httpserver.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

public class Server {

    private static final int ADDRESS = 8001;
    private static final int PORT = 0;

    private Server() {
    }

    /**
    * Basic server setup.
    * https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html
    * 
    * @param  args  An array of strings where [0] is the Keystore 'path' and [1] is the Keystore 'password'.
    */
    public static void main(String[] args) throws Exception {
        try {
            CoordinateDatabase db = CoordinateDatabase.getInstance();
            HttpsServer server = HttpsServer.create(new InetSocketAddress(ADDRESS), PORT);
            UserAuthenticator userAuthenticator = new UserAuthenticator("coordinates");
            SSLContext sslContext = coordinatesServerSSLContext(args[0], args[1]);
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    InetSocketAddress remote = params.getClientAddress();
                    SSLContext c = getSSLContext();
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);
                }
            });
            server.createContext("/coordinates", new CoordinatesHandler()).setAuthenticator(userAuthenticator);
            server.createContext("/comment", new CommentHandler()).setAuthenticator(new UserAuthenticator("comment"));
            server.createContext("/registration", new RegistrationHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            boolean running = true;
            while (running) {
                String input = br.readLine();
                if (input.equals("/quit")) {
                    running = false;
                    server.stop(3);
                    db.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static SSLContext coordinatesServerSSLContext(String file, String pwd) throws Exception {
        char[] passphrase = pwd.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(file), passphrase);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ssl;
    }
}
