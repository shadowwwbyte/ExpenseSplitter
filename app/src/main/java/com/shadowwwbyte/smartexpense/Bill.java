package com.shadowwwbyte.smartexpense;
import java.util.Map;
import java.util.HashMap;

public class Bill {
    private String title;
    private String description;
    private double total;
    private int payerId;                              // -1 if multi-payer
    private Map<Integer, Double> individualAmounts = new HashMap<>();  // who owes what
    private Map<Integer, Double> multiPayers = new HashMap<>();        // who paid what

    public Bill(String title, String description, double total, int payerId) {
        this.title = title; this.description = description == null ? "" : description;
        this.total = total; this.payerId = payerId;
    }

    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d == null ? "" : d; }
    public double getTotal() { return total; }
    public void setTotal(double t) { this.total = t; }
    public int getPayerId() { return payerId; }
    public void setPayerId(int id) { this.payerId = id; }

    // Individual owed amounts
    public Map<Integer, Double> getIndividualAmounts() { return individualAmounts; }
    public void setIndividualAmount(int pid, double amt) { individualAmounts.put(pid, amt); }
    public void clearIndividualAmounts() { individualAmounts.clear(); }
    public boolean hasIndividualAmounts() { return !individualAmounts.isEmpty(); }

    // Multi-payer contributions
    public Map<Integer, Double> getMultiPayers() { return multiPayers; }
    public void setMultiPayer(int pid, double amt) { multiPayers.put(pid, amt); }
    public void clearMultiPayers() { multiPayers.clear(); }
    public boolean isMultiPayer() { return !multiPayers.isEmpty(); }

    // Display helper: single payer name or "Multiple"
    public String payerLabel(ExpenseManager mgr) {
        if (isMultiPayer()) return "Multiple contributors";
        Person p = mgr.getPersonById(payerId);
        return p != null ? p.getName() : "Unknown";
    }

    @Override public String toString() {
        return title + " : " + String.format("%.2f", total)
            + (description.isEmpty() ? "" : " (" + description + ")");
    }
}
