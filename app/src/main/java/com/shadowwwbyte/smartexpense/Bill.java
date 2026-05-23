package com.shadowwwbyte.smartexpense;
import java.util.Map;
import java.util.HashMap;

public class Bill {
    private String title;
    private String description;
    private double total;
    private int payerId;
    private Map<Integer, Double> individualAmounts = new HashMap<>();

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
    public Map<Integer, Double> getIndividualAmounts() { return individualAmounts; }
    public void setIndividualAmount(int pid, double amt) { individualAmounts.put(pid, amt); }
    public void clearIndividualAmounts() { individualAmounts.clear(); }
    public boolean hasIndividualAmounts() { return !individualAmounts.isEmpty(); }

    @Override public String toString() {
        return title + " : " + String.format("%.2f", total) + (description.isEmpty() ? "" : " (" + description + ")");
    }
}
