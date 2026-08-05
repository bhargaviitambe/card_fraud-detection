
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        String jsonPath = "data.json";
        if (args.length > 0) jsonPath = args[0];

        String raw = new String(Files.readAllBytes(Paths.get(jsonPath)));
        JsonObject root = (JsonObject) JsonParser.parse(raw);

        System.out.println("   CREDIT CARD FRAUD DETECTION SIMULATOR        ");

        // Build FraudDetector from config
        JsonObject cfg = root.getObject("fraudChecks");
        FraudDetector detector = new FraudDetector();
        detector.addCheck(new HighAmountCheck(cfg.getDouble("highAmountLimit")));
        detector.addCheck(new UnusualLocationCheck());
        detector.addCheck(new NightTimeCheck(cfg.getInt("nightTimeStart"), cfg.getInt("nightTimeEnd")));
        detector.addCheck(new RapidTransactionCheck(cfg.getInt("rapidMaxAllowed"), cfg.getInt("rapidWithinSeconds")));
        detector.addCheck(new ForeignTransactionCheck());

        // Build Customers
        Map<String, Customer> customerMap = new HashMap<>();
        JsonArray customersArr = root.getArray("customers");
        for (int i = 0; i < customersArr.size(); i++) {
            JsonObject c = customersArr.getObject(i);
            customerMap.put(c.getString("id"),
            new Customer(c.getString("id"), c.getString("name"), c.getString("email"), c.getString("city")));
        }

        // Build Cards + Bank
        Map<String, CreditCard> cardMap = new HashMap<>();
        Bank bank = new Bank(root.getObject("bank").getString("name"), detector);
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

        // Process Scenarios
        JsonArray scenarios = root.getArray("scenarios");
        for (int s = 0; s < scenarios.size(); s++) {
            JsonObject scenario = scenarios.getObject(s);
            System.out.println("\n--- " + scenario.getString("label") + " ---");

            LocalDateTime base = LocalDateTime.now()
                .withHour(scenario.getInt("baseHour"))
                .withMinute(scenario.getInt("baseMinute"))
                .withSecond(scenario.getInt("baseSecond"))
                .withNano(0);

            JsonArray txns = scenario.getArray("transactions");
            for (int t = 0; t < txns.size(); t++) {
                JsonObject tj = txns.getObject(t);
                LocalDateTime txnTime = base;
                if (tj.has("offsetMinutes"))  txnTime = base.plusMinutes(tj.getLong("offsetMinutes"));
                else if (tj.has("offsetSeconds")) txnTime = base.plusSeconds(tj.getLong("offsetSeconds"));

                bank.processTransaction(new Transaction(
                    cardMap.get(tj.getString("cardNumber")),
                    tj.getDouble("amount"),
                    tj.getString("merchant"),
                    tj.getString("location"),
                    txnTime,
                    tj.getBoolean("isForeign")
                ));
            }
        }

        new Report().printSummary(bank);
    }
}