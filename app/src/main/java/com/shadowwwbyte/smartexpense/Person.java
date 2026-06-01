package com.shadowwwbyte.smartexpense;
public class Person {
    private static int COUNTER = 0;
    private int id;
    private String name;

    public Person(String name) { this.id = ++COUNTER; this.name = name; }

    // Restore a saved person with their original ID (does NOT increment COUNTER)
    public void forceId(int savedId) { this.id = savedId; }

    // Call after loading all saved persons so new persons get IDs above all saved ones
    public static void advanceCounter(int maxSavedId) {
        if (maxSavedId > COUNTER) COUNTER = maxSavedId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    @Override public String toString() { return name; }
}
