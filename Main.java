// import java.time.LocalDateTime;

// public class Main {

//     public static void main(String[] args) {

//         System.out.println("   CREDIT CARD FRAUD DETECTION SIMULATOR        ");

//         Customer alice   = new Customer("Alice Sharma", "alice@email.com",   "Mumbai");
//         Customer bob     = new Customer("Bob Mehta",    "bob@email.com",     "Delhi");
//         Customer charlie = new Customer("Charlie Roy",  "charlie@email.com", "Bangalore");
//         CreditCard aliceCard   = new CreditCard("4111000000000001", alice,   150000.0);
//         CreditCard bobCard     = new CreditCard("4111000000000002", bob,      80000.0);
//         CreditCard charlieCard = new CreditCard("4111000000000003", charlie, 300000.0);

//         FraudDetector detector = new FraudDetector();
//         detector.addCheck(new HighAmountCheck(50000.0));  
//         detector.addCheck(new UnusualLocationCheck());          
//         detector.addCheck(new NightTimeCheck(0, 4));            
//         detector.addCheck(new RapidTransactionCheck(3, 60));   
//         detector.addCheck(new ForeignTransactionCheck());       

//         Bank bank = new Bank("State Bank of India", detector);
//         bank.registerCard(aliceCard);
//         bank.registerCard(bobCard);
//         bank.registerCard(charlieCard);


//         // Normal everyday spending 

//         System.out.println("\nSCENARIO 1: Normal Spending ");
//         LocalDateTime day = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0);

//         bank.processTransaction(new Transaction(
//             aliceCard, 450.0,  "Reliance Fresh",    "Mumbai", day,                false));
//         bank.processTransaction(new Transaction(
//             aliceCard, 1200.0, "Zomato",            "Mumbai", day.plusMinutes(30), false));
//         bank.processTransaction(new Transaction(
//             aliceCard, 8500.0, "Croma Electronics", "Mumbai", day.plusHours(2),    false));

//         //High-value foreign 
//         System.out.println("\n SCENARIO 2: High-Value Foreign Transaction ");
//         LocalDateTime morning = LocalDateTime.now().withHour(11).withMinute(0).withSecond(0);

//         bank.processTransaction(new Transaction(
//             bobCard, 2000.0,  "D-Mart",          "Delhi",     morning,             false));
//         bank.processTransaction(new Transaction(
//             bobCard, 72000.0, "Luxury Jewellers", "Dubai, UAE", morning.plusHours(1), true));

//         // Rapid transactions (Charlie)
    
//         System.out.println("\n--- SCENARIO 3: Rapid-Fire Transactions (Charlie) ---");
//         LocalDateTime rapid = LocalDateTime.now().withHour(15).withMinute(0).withSecond(0);

//         bank.processTransaction(new Transaction(
//             charlieCard, 800.0,  "Amazon",    "Bangalore", rapid,                false));
//         bank.processTransaction(new Transaction(
//             charlieCard, 950.0,  "Flipkart",  "Bangalore", rapid.plusSeconds(8),  false));
//         bank.processTransaction(new Transaction(
//             charlieCard, 1100.0, "Myntra",    "Bangalore", rapid.plusSeconds(16), false));
//         bank.processTransaction(new Transaction(
//             charlieCard, 750.0,  "Swiggy",    "Bangalore", rapid.plusSeconds(24), false));

//         // ------------------------------------------------
//         // SCENARIO 4: Late-night transactions (Alice)
//         // 2am purchases in Goa — triggers Night Time + Location checks
//         // ------------------------------------------------
//         System.out.println("\n--- SCENARIO 4: Late-Night Transactions (Alice) ---");
//         LocalDateTime night = LocalDateTime.now().withHour(2).withMinute(15).withSecond(0);

//         bank.processTransaction(new Transaction(
//             aliceCard, 12000.0, "Casino Royale",  "Goa", night,                false));
//         bank.processTransaction(new Transaction(
//             aliceCard, 4000.0,  "ATM Withdrawal", "Goa", night.plusMinutes(5), false));

//         // ------------------------------------------------
//         // SCENARIO 5: Foreign transactions (Bob)
//         // Bob's card is already blocked from Scenario 2 — all rejected
//         // ------------------------------------------------
//         System.out.println("\n--- SCENARIO 5: Foreign Transactions (Bob) ---");
//         LocalDateTime eve = LocalDateTime.now().withHour(18).withMinute(0).withSecond(0);

//         bank.processTransaction(new Transaction(
//             bobCard, 9500.0,  "Amazon UK", "London, UK", eve.plusHours(1), true));
//         bank.processTransaction(new Transaction(
//             bobCard, 15000.0, "Harrods",   "London, UK", eve.plusHours(2), true));

//         // ------------------------------------------------
//         // SCENARIO 6: Velocity attack (Charlie)
//         // 6 transactions in 2 minutes from an unknown city
//         // ------------------------------------------------
//         System.out.println("\n--- SCENARIO 6: Velocity Attack (Charlie) ---");
//         LocalDateTime attack = LocalDateTime.now().withHour(13).withMinute(0).withSecond(0);

//         for (int i = 1; i <= 6; i++) {
//             bank.processTransaction(new Transaction(
//                 charlieCard,
//                 500.0 * i,
//                 "Suspicious Merchant " + i,
//                 "Unknown City",
//                 attack.plusSeconds(i * 15L),
//                 false
//             ));
//         }

//         // ------------------------------------------------
//         // Print final report
//         // ------------------------------------------------
//         Report report = new Report();
//         report.printSummary(bank);
//     }
// }
// ============================================================
// Main.java — reads everything from data.json, no hardcoding
// ============================================================
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
                new Customer(c.getString("name"), c.getString("email"), c.getString("city")));
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