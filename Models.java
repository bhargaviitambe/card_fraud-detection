// ============================================================
// Models.java
// Contains all the data classes used in the simulator:
//   - Customer
//   - CreditCard
//   - Transaction
// OOP concept: Encapsulation — private fields, public getters.
// ============================================================

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// ------------------------------------------------------------
// Represents a bank customer who owns a credit card
// ------------------------------------------------------------
class Customer {

    private String name;
    private String email;
    private String city;   // Home city — used to detect unusual locations

    public Customer(String name, String email, String city) {
        this.name  = name;
        this.email = email;
        this.city  = city;
    }

    public String getName()  { return name; }
    public String getEmail() { return email; }
    public String getCity()  { return city; }

    @Override
    public String toString() {
        return name + " (" + city + ")";
    }
}

// ------------------------------------------------------------
// Represents a credit card belonging to a Customer
// ------------------------------------------------------------
class CreditCard {

    private String cardNumber;
    private Customer owner;       // Which customer owns this card
    private double creditLimit;
    private double balance;       // How much credit is left
    private boolean isBlocked;
    private ArrayList<Transaction> transactionHistory;

    public CreditCard(String cardNumber, Customer owner, double creditLimit) {
        this.cardNumber  = cardNumber;
        this.owner       = owner;
        this.creditLimit = creditLimit;
        this.balance     = creditLimit;   // Starts fully available
        this.isBlocked   = false;
        this.transactionHistory = new ArrayList<>();
    }

    // Try to charge the card — returns false if blocked or no balance
    public boolean charge(double amount) {
        if (isBlocked) return false;
        if (amount > balance) return false;
        balance -= amount;
        return true;
    }

    public void block() { this.isBlocked = true; }

    public void addTransaction(Transaction t) { transactionHistory.add(t); }

    public ArrayList<Transaction> getTransactionHistory() { return transactionHistory; }

    // Show only last 4 digits for privacy: e.g. "**** 0001"
    public String getMaskedNumber() {
        return "**** " + cardNumber.substring(cardNumber.length() - 4);
    }

    public String getCardNumber()  { return cardNumber; }
    public Customer getOwner()     { return owner; }
    public double getBalance()     { return balance; }
    public boolean isBlocked()     { return isBlocked; }

    @Override
    public String toString() {
        return getMaskedNumber() + " | Owner: " + owner.getName()
               + " | Balance: Rs." + balance;
    }
}

// ------------------------------------------------------------
// Represents one transaction (purchase/payment attempt)
// ------------------------------------------------------------
class Transaction {

    private static int counter = 1;  // Auto-incrementing ID counter

    private String transactionId;
    private CreditCard card;
    private double amount;
    private String merchant;
    private String location;
    private LocalDateTime time;
    private boolean isForeign;   // Was this an international transaction?
    private String status;       // "APPROVED", "FLAGGED", or "BLOCKED"
    private String fraudReason;  // Why it was flagged (empty if clean)

    public Transaction(CreditCard card, double amount, String merchant,
                       String location, LocalDateTime time, boolean isForeign) {
        this.transactionId = "TXN" + String.format("%03d", counter++);
        this.card      = card;
        this.amount    = amount;
        this.merchant  = merchant;
        this.location  = location;
        this.time      = time;
        this.isForeign = isForeign;
        this.status    = "PENDING";
        this.fraudReason = "";
    }

    public String getTransactionId() { return transactionId; }
    public CreditCard getCard()      { return card; }
    public double getAmount()        { return amount; }
    public String getMerchant()      { return merchant; }
    public String getLocation()      { return location; }
    public LocalDateTime getTime()   { return time; }
    public boolean isForeign()       { return isForeign; }
    public String getStatus()        { return status; }
    public String getFraudReason()   { return fraudReason; }

    public void setStatus(String status)       { this.status = status; }
    public void setFraudReason(String reason)  { this.fraudReason = reason; }

    public boolean isFlagged() {
        return status.equals("FLAGGED") || status.equals("BLOCKED");
    }

    @Override
    public String toString() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        String icon = status.equals("APPROVED") ? "[OK]   "
                    : status.equals("FLAGGED")  ? "[FLAG] "
                    : status.equals("BLOCKED")  ? "[BLOCK]"
                    : "[?]    ";

        String line = icon + " " + transactionId
                    + " | " + time.format(fmt)
                    + " | " + String.format("%-22s", merchant)
                    + " | Rs." + String.format("%8.2f", amount)
                    + " | " + location;

        if (!fraudReason.isEmpty()) {
            line += "\n         Reason: " + fraudReason;
        }
        return line;
    }
}
