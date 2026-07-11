// ============================================================
// Fraud.java
// Contains everything related to fraud detection and banking:
//
//   FraudCheck            — abstract base class for all checks
//   HighAmountCheck       — flags large transactions
//   UnusualLocationCheck  — flags wrong city
//   NightTimeCheck        — flags late-night transactions
//   RapidTransactionCheck — flags too many transactions too fast
//   ForeignTransactionCheck — flags international transactions
//   FraudDetector         — runs all checks on a transaction
//   Bank                  — processes transactions, blocks cards
//   Report                — prints the final summary
//
// OOP concepts used:
//   Abstraction    — FraudCheck is abstract (cannot be created directly)
//   Inheritance    — all 5 check classes extend FraudCheck
//   Polymorphism   — FraudDetector calls check() on each, each behaves differently
//   Encapsulation  — private fields with public methods throughout
// ============================================================

import java.util.ArrayList;
import java.time.temporal.ChronoUnit;

abstract class FraudCheck {

    protected String checkName;

    public FraudCheck(String checkName) {
        this.checkName = checkName;
    }

    public abstract String check(Transaction transaction,
                                 ArrayList<Transaction> history);

    public String getCheckName() { return checkName; }
}

class HighAmountCheck extends FraudCheck {

    private double limit;

    public HighAmountCheck(double limit) {
        super("High Amount");
        this.limit = limit;
    }

    @Override
    public String check(Transaction transaction, ArrayList<Transaction> history) {
        if (transaction.getAmount() > limit) {
            return "Amount Rs." + transaction.getAmount()
                   + " exceeds limit of Rs." + limit;
        }
        return "";
    }
}

class UnusualLocationCheck extends FraudCheck {

    public UnusualLocationCheck() {
        super("Unusual Location");
    }

    @Override
    public String check(Transaction transaction, ArrayList<Transaction> history) {
        String homeCity    = transaction.getCard().getOwner().getCity().toLowerCase();
        String txnLocation = transaction.getLocation().toLowerCase();

        if (!txnLocation.contains(homeCity) && !homeCity.contains(txnLocation)) {
            return "Transaction in '" + transaction.getLocation()
                   + "' ;usual location: '" + transaction.getCard().getOwner().getCity() + "'";
        }
        return "";
    }
}

class NightTimeCheck extends FraudCheck {

    private int startHour;
    private int endHour;

    public NightTimeCheck(int startHour, int endHour) {
        super("Unusual Time");
        this.startHour = startHour;
        this.endHour   = endHour;
    }

    @Override
    public String check(Transaction transaction, ArrayList<Transaction> history) {
        int hour = transaction.getTime().getHour();
        if (hour >= startHour && hour < endHour) {
            return "Transaction at suspicious hour " + hour + ":00"
                   + " (flagged window: " + startHour + ":00 to " + endHour + ":00)";
        }
        return "";
    }
}

class RapidTransactionCheck extends FraudCheck {

    private int maxAllowed;
    private int withinSeconds;

    public RapidTransactionCheck(int maxAllowed, int withinSeconds) {
        super("Rapid Transactions");
        this.maxAllowed    = maxAllowed;
        this.withinSeconds = withinSeconds;
    }

    @Override
    public String check(Transaction transaction, ArrayList<Transaction> history) {
        int count = 0;

        for (Transaction past : history) {
            long diff = ChronoUnit.SECONDS.between(past.getTime(), transaction.getTime());
            if (diff >= 0 && diff <= withinSeconds) {
                count++;
            }
        }

        if (count >= maxAllowed) {
            return (count + 1) + " transactions within "
                   + withinSeconds + " seconds (max allowed: " + maxAllowed + ")";
        }
        return "";
    }
}

class ForeignTransactionCheck extends FraudCheck {

    public ForeignTransactionCheck() {
        super("Foreign Transaction");
    }

    @Override
    public String check(Transaction transaction, ArrayList<Transaction> history) {
        if (transaction.isForeign()) {
            return "International transaction at: " + transaction.getLocation();
        }
        return "";
    }
}

class FraudDetector {

    private ArrayList<FraudCheck> checks;

    public FraudDetector() {
        checks = new ArrayList<>();
    }

    public void addCheck(FraudCheck check) {
        checks.add(check);
    }

    public ArrayList<String> runChecks(Transaction transaction,
                                       ArrayList<Transaction> history) {
        ArrayList<String> reasons = new ArrayList<>();

        for (FraudCheck check : checks) {
            String result = check.check(transaction, history);
            if (!result.isEmpty()) {
                reasons.add("[" + check.getCheckName() + "] " + result);
            }
        }

        return reasons;
    }
}
class Bank {

    private String bankName;
    private ArrayList<CreditCard> cards;
    private ArrayList<Transaction> allTransactions;
    private FraudDetector fraudDetector;

    public Bank(String bankName, FraudDetector fraudDetector) {
        this.bankName        = bankName;
        this.fraudDetector   = fraudDetector;
        this.cards           = new ArrayList<>();
        this.allTransactions = new ArrayList<>();
    }

    public void registerCard(CreditCard card) {
        cards.add(card);
        System.out.println("Card registered: " + card);
    }

    public void processTransaction(Transaction transaction) {
        CreditCard card = transaction.getCard();

        if (card.isBlocked()) {
            transaction.setStatus("BLOCKED");
            transaction.setFraudReason("Card is already blocked.");
            save(card, transaction);
            return;
        }

        //fraud checks
        ArrayList<String> reasons = fraudDetector.runChecks(
            transaction, card.getTransactionHistory()
        );

        if (reasons.isEmpty()) {
            if (card.charge(transaction.getAmount())) {
                transaction.setStatus("APPROVED");
            } else {
                transaction.setStatus("BLOCKED");
                transaction.setFraudReason("Insufficient balance.");
            }
        } else {
            transaction.setFraudReason(String.join(" | ", reasons));

            if (reasons.size() >= 3) {
                transaction.setStatus("BLOCKED");
                card.block();
                System.out.println("  !! CARD BLOCKED: " + card.getMaskedNumber());
            } else {
                transaction.setStatus("FLAGGED");
            }
        }

        save(card, transaction);
    }

    // print all
    private void save(CreditCard card, Transaction transaction) {
        card.addTransaction(transaction);
        allTransactions.add(transaction);
        System.out.println(transaction);
    }

    public String getBankName()                        { return bankName; }
    public ArrayList<CreditCard> getCards()            { return cards; }
    public ArrayList<Transaction> getAllTransactions() { return allTransactions; }
}

// summary
class Report {

    public void printSummary(Bank bank) {
        ArrayList<Transaction> all = bank.getAllTransactions();

        int approved = 0, flagged = 0, blocked = 0;
        double totalAmount = 0, suspiciousAmount = 0;

        for (Transaction t : all) {
            totalAmount += t.getAmount();
            if      (t.getStatus().equals("APPROVED")) approved++;
            else if (t.getStatus().equals("FLAGGED"))  { flagged++;  suspiciousAmount += t.getAmount(); }
            else if (t.getStatus().equals("BLOCKED"))  { blocked++;  suspiciousAmount += t.getAmount(); }
        }

        System.out.println("FRAUD DETECTION REPORT");
        System.out.println("Bank              : " + bank.getBankName());
        System.out.println("Total Transactions: " + all.size());
        System.out.println("  Approved        : " + approved);
        System.out.println("  Flagged         : " + flagged);
        System.out.println("  Blocked         : " + blocked);
        System.out.printf( "Total Amount        : Rs.%.2f%n", totalAmount);
        System.out.printf( "Suspicious Amount   : Rs.%.2f%n", suspiciousAmount);

        System.out.println("\n Per Card Summary ");
        for (CreditCard card : bank.getCards()) {
            int suspicious = 0;
            for (Transaction t : card.getTransactionHistory()) {
                if (t.isFlagged()) suspicious++;
            }
            System.out.println("  " + card.getMaskedNumber()
                + " | " + card.getOwner().getName()
                + " | Txns: " + card.getTransactionHistory().size()
                + " | Suspicious: " + suspicious
                + " | Blocked: " + (card.isBlocked() ? "YES" : "NO"));
        }

        System.out.println("\n Flagged / Blocked Transactions ");
        boolean found = false;
        for (Transaction t : all) {
            if (t.isFlagged()) {
                found = true;
                System.out.println("  " + t.getTransactionId()
                    + " | " + t.getCard().getOwner().getName()
                    + " | Rs." + t.getAmount()
                    + " | " + t.getMerchant()
                    + "\n    Reason: " + t.getFraudReason());
            }
        }
        if (!found) System.out.println("  No suspicious transactions found.");
    }
}
