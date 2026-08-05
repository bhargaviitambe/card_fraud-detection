// ============================================================
// TransactionServer.java
// A lightweight HTTP server (built-in JDK, no external deps)
// that exposes the fraud detection engine over HTTP so a
// simple web frontend can submit transactions and see results.
//
// Persistence: all customers, cards, and transactions are saved
// to store.json after every change, and reloaded from it on
// startup, so nothing is lost when the server restarts.
// data.json is only used the very first time (as seed data) —
// after that, store.json is the source of truth.
//
// Supports multiple cards per customer: /api/customers creates
// a brand-new customer + their first card, while /api/cards
// (POST) adds an additional card to an existing customer.
// ============================================================

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionServer {

    private static final String STORE_PATH = "store.json";
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static Bank bank;
    private static FraudDetector detector;
    private static Map<String, Customer> customerMap = new HashMap<>();
    private static Map<String, CreditCard> cardMap = new HashMap<>();
    private static int customerIdCounter = 1;

    public static void main(String[] args) throws Exception {
        String jsonPath = args.length > 0 ? args[0] : "data.json";
        loadFraudConfig(jsonPath);

        if (Files.exists(Paths.get(STORE_PATH))) {
            loadFromStore();
        } else {
            seedFromDataJson(jsonPath);
            persistStore();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/cards", new CardsHandler());
        server.createContext("/api/customers", new CustomersHandler());
        server.createContext("/api/transactions", new TransactionsHandler());
        server.createContext("/api/transaction", new TransactionHandler());
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("Fraud detection server running at http://localhost:8080");
        System.out.println("Persisting data to " + Paths.get(STORE_PATH).toAbsolutePath());
    }

    // ---- Load only the fraud-check configuration + bank name from data.json ----
    private static void loadFraudConfig(String jsonPath) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(jsonPath)));
        JsonObject root = (JsonObject) JsonParser.parse(raw);

        JsonObject cfg = root.getObject("fraudChecks");
        detector = new FraudDetector();
        detector.addCheck(new HighAmountCheck(cfg.getDouble("highAmountLimit")));
        detector.addCheck(new UnusualLocationCheck());
        detector.addCheck(new NightTimeCheck(cfg.getInt("nightTimeStart"), cfg.getInt("nightTimeEnd")));
        detector.addCheck(new RapidTransactionCheck(cfg.getInt("rapidMaxAllowed"), cfg.getInt("rapidWithinSeconds")));
        detector.addCheck(new ForeignTransactionCheck());

        bank = new Bank(root.getObject("bank").getString("name"), detector);
    }

    // ---- First-ever run: seed customers/cards from data.json ----
    private static void seedFromDataJson(String jsonPath) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(jsonPath)));
        JsonObject root = (JsonObject) JsonParser.parse(raw);

        JsonArray customersArr = root.getArray("customers");
        for (int i = 0; i < customersArr.size(); i++) {
            JsonObject c = customersArr.getObject(i);
            String id = c.getString("id");
            customerMap.put(id, new Customer(id, c.getString("name"), c.getString("email"), c.getString("city")));
            bumpCustomerCounter(id);
        }

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

    // ---- Every subsequent run: load everything from store.json ----
    private static void loadFromStore() throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(STORE_PATH)));
        JsonObject root = (JsonObject) JsonParser.parse(raw);

        JsonArray customersArr = root.getArray("customers");
        for (int i = 0; i < customersArr.size(); i++) {
            JsonObject c = customersArr.getObject(i);
            String id = c.getString("id");
            customerMap.put(id, new Customer(id, c.getString("name"), c.getString("email"), c.getString("city")));
            bumpCustomerCounter(id);
        }

        JsonArray cardsArr = root.getArray("cards");
        for (int i = 0; i < cardsArr.size(); i++) {
            JsonObject cj = cardsArr.getObject(i);
            CreditCard card = new CreditCard(
                cj.getString("cardNumber"),
                customerMap.get(cj.getString("customerId")),
                cj.getDouble("creditLimit")
            );
            card.setBalance(cj.getDouble("balance"));
            card.setBlocked(cj.getBoolean("blocked"));
            cardMap.put(cj.getString("cardNumber"), card);
            bank.registerCard(card);
        }

        int maxTxnNum = 0;
        JsonArray txnsArr = root.getArray("transactions");
        for (int i = 0; i < txnsArr.size(); i++) {
            JsonObject tj = txnsArr.getObject(i);
            CreditCard card = cardMap.get(tj.getString("cardNumber"));
            if (card == null) continue;

            String id = tj.getString("transactionId");
            LocalDateTime time = LocalDateTime.parse(tj.getString("time"), TIME_FMT);

            Transaction t = new Transaction(
                id, card, tj.getDouble("amount"), tj.getString("merchant"),
                tj.getString("location"), time, tj.getBoolean("isForeign"),
                tj.getString("status"), tj.has("reason") ? tj.getString("reason") : ""
            );
            bank.restoreTransaction(card, t);

            String digits = id.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                maxTxnNum = Math.max(maxTxnNum, Integer.parseInt(digits));
            }
        }
        Transaction.setNextCounter(maxTxnNum + 1);
    }

    private static void bumpCustomerCounter(String id) {
        String digits = id.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            customerIdCounter = Math.max(customerIdCounter, Integer.parseInt(digits) + 1);
        }
    }

    // ---- Write the full current state to store.json ----
    private static synchronized void persistStore() throws IOException {
        StringBuilder json = new StringBuilder("{");

        json.append("\"customers\":[");
        boolean first = true;
        for (Customer c : customerMap.values()) {
            if (!first) json.append(",");
            first = false;
            json.append("{")
                .append("\"id\":\"").append(esc(c.getId())).append("\",")
                .append("\"name\":\"").append(esc(c.getName())).append("\",")
                .append("\"email\":\"").append(esc(c.getEmail())).append("\",")
                .append("\"city\":\"").append(esc(c.getCity())).append("\"")
                .append("}");
        }
        json.append("],");

        json.append("\"cards\":[");
        first = true;
        for (CreditCard card : cardMap.values()) {
            if (!first) json.append(",");
            first = false;
            json.append("{")
                .append("\"cardNumber\":\"").append(card.getCardNumber()).append("\",")
                .append("\"customerId\":\"").append(esc(card.getOwner().getId())).append("\",")
                .append("\"creditLimit\":").append(card.getCreditLimit()).append(",")
                .append("\"balance\":").append(card.getBalance()).append(",")
                .append("\"blocked\":").append(card.isBlocked())
                .append("}");
        }
        json.append("],");

        json.append("\"transactions\":[");
        first = true;
        for (Transaction t : bank.getAllTransactions()) {
            if (!first) json.append(",");
            first = false;
            json.append("{")
                .append("\"transactionId\":\"").append(t.getTransactionId()).append("\",")
                .append("\"cardNumber\":\"").append(t.getCard().getCardNumber()).append("\",")
                .append("\"amount\":").append(t.getAmount()).append(",")
                .append("\"merchant\":\"").append(esc(t.getMerchant())).append("\",")
                .append("\"location\":\"").append(esc(t.getLocation())).append("\",")
                .append("\"time\":\"").append(t.getTime().format(TIME_FMT)).append("\",")
                .append("\"isForeign\":").append(t.isForeign()).append(",")
                .append("\"status\":\"").append(t.getStatus()).append("\",")
                .append("\"reason\":\"").append(esc(t.getFraudReason())).append("\"")
                .append("}");
        }
        json.append("]");

        json.append("}");

        Files.write(Paths.get(STORE_PATH), json.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---- GET /api/cards — list cards (dropdown). POST /api/cards — add a card to an existing customer ----
    static class CardsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange, "GET, POST");
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCardToExistingCustomer(exchange);
                return;
            }

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (CreditCard card : cardMap.values()) {
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"cardNumber\":\"").append(card.getCardNumber()).append("\",")
                    .append("\"masked\":\"").append(card.getMaskedNumber()).append("\",")
                    .append("\"owner\":\"").append(esc(card.getOwner().getName())).append("\",")
                    .append("\"customerId\":\"").append(esc(card.getOwner().getId())).append("\",")
                    .append("\"city\":\"").append(esc(card.getOwner().getCity())).append("\",")
                    .append("\"balance\":").append(card.getBalance()).append(",")
                    .append("\"blocked\":").append(card.isBlocked())
                    .append("}");
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        }

        // POST /api/cards — { "customerId": "c1", "cardNumber": "...", "creditLimit": 50000 }
        // Adds a new card to an EXISTING customer (unlike /api/customers, which creates a new person).
        private void addCardToExistingCustomer(HttpExchange exchange) throws IOException {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                JsonObject req = (JsonObject) JsonParser.parse(body);

                Customer customer = customerMap.get(req.getString("customerId"));
                if (customer == null) {
                    sendJson(exchange, 400, "{\"error\":\"Unknown customer\"}");
                    return;
                }

                String cardNumber = req.getString("cardNumber");
                if (cardMap.containsKey(cardNumber)) {
                    sendJson(exchange, 400, "{\"error\":\"That card number is already registered\"}");
                    return;
                }

                CreditCard card = new CreditCard(cardNumber, customer, req.getDouble("creditLimit"));
                cardMap.put(cardNumber, card);
                bank.registerCard(card);
                persistStore();

                StringBuilder json = new StringBuilder("{");
                json.append("\"cardNumber\":\"").append(card.getCardNumber()).append("\",");
                json.append("\"masked\":\"").append(card.getMaskedNumber()).append("\",");
                json.append("\"owner\":\"").append(esc(customer.getName())).append("\"");
                json.append("}");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + esc(String.valueOf(e.getMessage())) + "\"}");
            }
        }
    }

    // ---- GET /api/customers — list customers. POST /api/customers — create a new customer + first card ----
    static class CustomersHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange, "GET, POST");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                listCustomers(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Use GET or POST\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            try {
                JsonObject req = (JsonObject) JsonParser.parse(body);

                String cardNumber = req.getString("cardNumber");
                if (cardMap.containsKey(cardNumber)) {
                    sendJson(exchange, 400, "{\"error\":\"That card number is already registered\"}");
                    return;
                }

                String id = "c" + (customerIdCounter++);
                Customer customer = new Customer(
                    id, req.getString("name"), req.getString("email"), req.getString("city")
                );
                customerMap.put(id, customer);

                CreditCard card = new CreditCard(cardNumber, customer, req.getDouble("creditLimit"));
                cardMap.put(cardNumber, card);
                bank.registerCard(card);

                persistStore();

                StringBuilder json = new StringBuilder("{");
                json.append("\"cardNumber\":\"").append(card.getCardNumber()).append("\",");
                json.append("\"masked\":\"").append(card.getMaskedNumber()).append("\",");
                json.append("\"owner\":\"").append(esc(customer.getName())).append("\",");
                json.append("\"city\":\"").append(esc(customer.getCity())).append("\"");
                json.append("}");

                sendJson(exchange, 200, json.toString());
            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + esc(String.valueOf(e.getMessage())) + "\"}");
            }
        }

        // GET /api/customers — list of existing customers, each with how many cards they hold
        // (used to populate the "add another card to..." dropdown)
        private void listCustomers(HttpExchange exchange) throws IOException {
            Map<String, Integer> cardCounts = new HashMap<>();
            for (CreditCard card : cardMap.values()) {
                cardCounts.merge(card.getOwner().getId(), 1, Integer::sum);
            }

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Customer c : customerMap.values()) {
                if (!first) json.append(",");
                first = false;
                int count = cardCounts.getOrDefault(c.getId(), 0);
                json.append("{")
                    .append("\"id\":\"").append(esc(c.getId())).append("\",")
                    .append("\"name\":\"").append(esc(c.getName())).append("\",")
                    .append("\"city\":\"").append(esc(c.getCity())).append("\",")
                    .append("\"cardCount\":").append(count)
                    .append("}");
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    }

    // ---- GET /api/transactions?cardNumber=... — full transaction history for one card ----
    static class TransactionsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange, "GET");
                return;
            }

            String cardNumber = null;
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && kv[0].equals("cardNumber")) {
                        cardNumber = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                }
            }

            CreditCard card = cardMap.get(cardNumber);
            if (card == null) {
                sendJson(exchange, 400, "{\"error\":\"Unknown card number\"}");
                return;
            }

            List<Transaction> history = card.getTransactionHistory();

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (int i = history.size() - 1; i >= 0; i--) { // most recent first
                Transaction t = history.get(i);
                if (!first) json.append(",");
                first = false;
                json.append("{")
                    .append("\"transactionId\":\"").append(t.getTransactionId()).append("\",")
                    .append("\"amount\":").append(t.getAmount()).append(",")
                    .append("\"merchant\":\"").append(esc(t.getMerchant())).append("\",")
                    .append("\"location\":\"").append(esc(t.getLocation())).append("\",")
                    .append("\"time\":\"").append(t.getTime().format(TIME_FMT)).append("\",")
                    .append("\"isForeign\":").append(t.isForeign()).append(",")
                    .append("\"status\":\"").append(t.getStatus()).append("\",")
                    .append("\"reason\":\"").append(esc(t.getFraudReason())).append("\"")
                    .append("}");
            }
            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    }

    // ---- POST /api/transaction — run a manually entered transaction through fraud checks ----
    static class TransactionHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendCorsPreflight(exchange, "POST");
                return;
            }
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
                persistStore();

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

    // ---- Serves index.html / assets from the frontend build (or /public in dev) ----
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

    private static void sendCorsPreflight(HttpExchange exchange, String methods) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", methods + ", OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}