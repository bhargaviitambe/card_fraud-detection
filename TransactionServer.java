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

    // ---- Load bank + fraud detector from data.json (same logic as Main.java) ----
    private static void loadBank(String jsonPath) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(jsonPath)));
        JsonObject root = (JsonObject) JsonParser.parse(raw);

        JsonObject cfg = root.getObject("fraudChecks");
        FraudDetector detector = new FraudDetector();
        detector.addCheck(new HighAmountCheck(cfg.getDouble("highAmountLimit")));
        detector.addCheck(new UnusualLocationCheck());
        detector.addCheck(new NightTimeCheck(cfg.getInt("nightTimeStart"), cfg.getInt("nightTimeEnd")));
        detector.addCheck(new RapidTransactionCheck(cfg.getInt("rapidMaxAllowed"), cfg.getInt("rapidWithinSeconds")));
        detector.addCheck(new ForeignTransactionCheck());

        Map<String, Customer> customerMap = new HashMap<>();
        JsonArray customersArr = root.getArray("customers");
        for (int i = 0; i < customersArr.size(); i++) {
            JsonObject c = customersArr.getObject(i);
            customerMap.put(c.getString("id"),
                new Customer(c.getString("name"), c.getString("email"), c.getString("city")));
        }

        bank = new Bank(root.getObject("bank").getString("name"), detector);
        JsonArray cardsArr = root.getArray("cards");
        for (int i = 0; i < cardsArr.size(); i++) {
            JsonObject cj = cardsArr.getObject(i);
            CreditCard card = new CreditCard(
                cj.getString("cardNumber"),
                customerMap.get(cj.getString("customerId")),
                cj.getDouble("creditLimit")
            );
            cardMap.put(cj.getString("cardNumber"), card);
            bank.registerCard(card);
        }
    }

    // ---- GET /api/cards — list registered cards, used to populate the dropdown ----
    static class CardsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (CreditCard card : cardMap.values()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"cardNumber\":\"").append(card.getCardNumber()).append("\",")
                    .append("\"masked\":\"").append(card.getMaskedNumber()).append("\",")
                    .append("\"owner\":\"").append(esc(card.getOwner().getName())).append("\",")
                    .append("\"city\":\"").append(esc(card.getOwner().getCity())).append("\",")
                    .append("\"blocked\":").append(card.isBlocked())
                    .append("}");
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    }

    // ---- POST /api/transaction — run a manually entered transaction through fraud checks ----
    static class TransactionHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Use POST\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                JsonObject req = (JsonObject) JsonParser.parse(body);

                CreditCard card = cardMap.get(req.getString("cardNumber"));
                if (card == null) {
                    sendJson(exchange, 400, "{\"error\":\"Unknown card number\"}");
                    return;
                }

                Transaction txn = new Transaction(
                    card,
                    req.getDouble("amount"),
                    req.getString("merchant"),
                    req.getString("location"),
                    LocalDateTime.now(),
                    req.getBoolean("isForeign")
                );

                bank.processTransaction(txn);

                StringBuilder json = new StringBuilder("{");
                json.append("\"transactionId\":\"").append(txn.getTransactionId()).append("\",");
                json.append("\"status\":\"").append(txn.getStatus()).append("\",");
                json.append("\"cardHolder\":\"").append(esc(card.getOwner().getName())).append("\",");
                json.append("\"maskedCard\":\"").append(card.getMaskedNumber()).append("\",");
                json.append("\"cardBlocked\":").append(card.isBlocked()).append(",");
                json.append("\"reason\":\"").append(esc(txn.getFraudReason())).append("\"");
                json.append("}");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + esc(String.valueOf(e.getMessage())) + "\"}");
            }
        }
    }

    // ---- Serves index.html / style.css / script.js from the /public folder ----
    static class StaticFileHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";

            Path file = Paths.get("public" + path);
            if (!Files.exists(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";

            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}