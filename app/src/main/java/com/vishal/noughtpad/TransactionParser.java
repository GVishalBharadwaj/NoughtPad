package com.vishal.noughtpad;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {

    // Regex Patterns for Indian Banking SMS/Notifications

    // Pattern 1: "Rs. 500 debited..." or "INR 500 spent..."
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:rs\\.?|inr)\\s*(\\d+(?:\\.\\d{1,2})?)");

    // Pattern 2: "Debited", "Spent", "Sent", "Paid" etc.
    private static final Pattern DEBIT_KEYWORD_PATTERN = Pattern
            .compile("(?i)(debited|spent|sent|paid|transfer|withdrawal)");
    private static final Pattern CREDIT_KEYWORD_PATTERN = Pattern.compile("(?i)(credited|received|deposited|added)");

    // Pattern 3: Merchant/Payee Extraction
    // "to Zomato", "at Starbucks", "VPA: vishal@upi"
    private static final Pattern MERCHANT_PATTERN_1 = Pattern.compile(
            "(?i)(?:to|at|no\\.|vp[a-z])\\s+([a-zA-Z0-9\\s\\._@]+?)(?:\\s+(?:on|from|via|ref|bal|thru|through)|$)");

    // "Paid Rs 500 to Zomato"
    private static final Pattern MERCHANT_PATTERN_2 = Pattern
            .compile("(?i)(?:paid)\\s+(?:rs\\.?|inr)\\s*\\d+(?:\\.\\d+)?\\s+to\\s+([a-zA-Z0-9\\s\\._@]+)");

    public static class TransactionInfo {
        public double amount;
        public String merchant;
        public String description; // New field for clean description
        public long timestamp;
        public boolean isDebit; // true = expense, false = income

        @Override
        public String toString() {
            return (isDebit ? "Debit: " : "Credit: ") + amount + ", Merch: " + merchant + ", Desc: " + description;
        }
    }

    public static TransactionInfo parse(String message) {
        if (message == null)
            return null;
        message = message.trim().replaceAll("\\s+", " ");

        boolean isDebit = DEBIT_KEYWORD_PATTERN.matcher(message).find();
        boolean isCredit = CREDIT_KEYWORD_PATTERN.matcher(message).find();

        // Define pattern for upcoming bills, SIPs, statement generation
        boolean isBill = Pattern.compile(
                "(?i)(statement generated|payment due|min amount due|minimum amount due|bill generated|payment pending|sip of|auto pay|autopay)")
                .matcher(message).find();

        if (!isDebit && !isCredit && !isBill)
            return null; // Not a relevant transaction

        // 2. Extract Amount
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(message);
        if (!amountMatcher.find()) {
            return null;
        }

        TransactionInfo info = new TransactionInfo();
        try {
            info.amount = Double.parseDouble(amountMatcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }

        info.isDebit = isDebit || isBill; // Bills are future debits
        info.timestamp = System.currentTimeMillis();

        // If it's a bill, we flag it in the description or by a new boolean
        // We'll use a special string in description to flag it for the Listener
        boolean flagAsBill = isBill;

        // 3. Extract Merchant
        Matcher m1 = MERCHANT_PATTERN_1.matcher(message);
        Matcher m2 = MERCHANT_PATTERN_2.matcher(message);

        if (m2.find()) {
            info.merchant = cleanMerchantName(m2.group(1));
        } else if (m1.find()) {
            info.merchant = cleanMerchantName(m1.group(1));
        } else {
            info.merchant = isCredit ? "Unknown Source" : (isBill ? "Upcoming Bill" : "Unknown Merchant");
        }

        // 4. Generate Clean Description
        info.description = cleanDescription(message, info.merchant);

        if (flagAsBill) {
            info.description = "[BILL] " + info.description;
        }

        return info;
    }

    private static String cleanMerchantName(String name) {
        if (name == null)
            return "Unknown";
        String clean = name.trim();
        clean = clean.replaceAll("(?i)(via|on|ref|bal|thru|through).*", "").trim();
        if (clean.length() > 20)
            clean = clean.substring(0, 20) + "...";
        return clean;
    }

    private static String cleanDescription(String rawMessage, String merchant) {
        if (rawMessage == null)
            return "Transaction detected";

        // Remove Account Numbers (e.g. "A/c *1234", "ending 1234", "A/c 1234")
        String clean = rawMessage.replaceAll("(?i)(a/c|acct|account)\\s*[*x]*\\d+", "")
                .replaceAll("(?i)ending\\s+(?:in\\s+)?[*x]*\\d+", "");

        // Remove Balance Info (e.g. "Avail Bal Rs 100", "Clr Bal", "Net Bal")
        clean = clean.replaceAll("(?i)(avail|clr|net|main|led)\\s+bal(?:ance)?.*", "");

        // Remove Request/Reference IDs if they are long
        clean = clean.replaceAll("(?i)(ref|txnid|upi ref|bk id)\\s*[:\\-]?\\s*[a-z0-9]+", "");

        // Remove common prefixes already covered by metadata
        clean = clean.replaceAll("(?i)(debited|credited|sent|paid|received|spent)\\s+.*", "");

        // Clean up extra spaces/punctuation
        clean = clean.replaceAll("[:\\-]", " ").trim();
        clean = clean.replaceAll("\\s+", " ");

        // Construct a readable string
        StringBuilder builder = new StringBuilder();
        if (rawMessage.toLowerCase().contains("upi")) {
            builder.append("Payment via UPI");
        } else if (rawMessage.toLowerCase().contains("card") || rawMessage.toLowerCase().contains("atm")) {
            builder.append("Card Transaction");
        } else if (rawMessage.toLowerCase().contains("neft") || rawMessage.toLowerCase().contains("imps")) {
            builder.append("Bank Transfer");
        } else {
            builder.append("Transaction");
        }

        return builder.toString();
    }
}
