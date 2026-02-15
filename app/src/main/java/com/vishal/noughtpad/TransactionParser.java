package com.vishal.noughtpad;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {

    // Regex Patterns for Indian Banking SMS/Notifications

    // Pattern 1: "Rs. 500 debited..." or "INR 500 spent..."
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:rs\\.?|inr)\\s*(\\d+(?:\\.\\d{1,2})?)");

    // Pattern 2: "Debited", "Spent", "Sent", "Paid" - ensure it's a debit
    // transaction
    private static final Pattern DEBIT_KEYWORD_PATTERN = Pattern
            .compile("(?i)(debited|spent|sent|paid|transfer|withdrawal)");

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
        public String account; // Optional: last 4 digits
        public long timestamp;
        public boolean isDebit;

        @Override
        public String toString() {
            return "Amt: " + amount + ", Merch: " + merchant;
        }
    }

    public static TransactionInfo parse(String message) {
        if (message == null)
            return null;
        message = message.trim().replaceAll("\\s+", " "); // Normalize spaces

        // 1. Check if it's a Debit transaction
        if (!DEBIT_KEYWORD_PATTERN.matcher(message).find()) {
            return null; // Ignore credits, OTPs, offers
        }

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
        info.isDebit = true;
        info.timestamp = System.currentTimeMillis();

        // 3. Extract Merchant
        Matcher m1 = MERCHANT_PATTERN_1.matcher(message);
        Matcher m2 = MERCHANT_PATTERN_2.matcher(message);

        if (m2.find()) {
            info.merchant = cleanMerchantName(m2.group(1));
        } else if (m1.find()) {
            info.merchant = cleanMerchantName(m1.group(1));
        } else {
            info.merchant = "Unknown Merchant";
        }

        return info;
    }

    private static String cleanMerchantName(String name) {
        if (name == null)
            return "Unknown";
        String clean = name.trim();
        // Remove common trails
        clean = clean.replaceAll("(?i)(via|on|ref|bal|thru|through).*", "").trim();
        // Capitalize
        if (clean.length() > 20)
            clean = clean.substring(0, 20) + "...";
        return clean;
    }
}
