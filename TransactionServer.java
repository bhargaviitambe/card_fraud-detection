// ============================================================
// TransactionServer.java
// A lightweight HTTP server (built-in JDK, no external deps)
// that exposes the fraud detection engine over HTTP so a
// simple web frontend can submit transactions and see results.
// ============================================================

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TransactionServer {

    private static Bank bank;
    private static Map<String, CreditCard> cardMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        String jsonPath = args.length > 0 ? args[0] : "data.json";
        loadBank(jsonPath);

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/cards", new CardsHandler());
        server.createContext("/api/transaction", new TransactionHandler());
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Fraud detection server running at http://localhost:8080");
    }
