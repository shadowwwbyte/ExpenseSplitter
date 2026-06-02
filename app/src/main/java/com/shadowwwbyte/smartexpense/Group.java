package com.shadowwwbyte.smartexpense;

public class Group {
    private static int COUNTER = 0;
    private int id;
    private String name;
    private String description;
    private long createdAt;

    public Group(String name, String description) {
        this.id = ++COUNTER;
        this.name = name;
        this.description = description == null ? "" : description;
        this.createdAt = System.currentTimeMillis();
    }

    // Restore with original saved ID — does NOT increment COUNTER
    public void forceId(int savedId) { this.id = savedId; }

    // After loading all saved groups, call this so new groups get IDs above all saved ones
    public static void advanceCounter(int maxSavedId) {
        if (maxSavedId > COUNTER) COUNTER = maxSavedId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d == null ? "" : d; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long t) { this.createdAt = t; }

    @Override public String toString() { return name; }
}
