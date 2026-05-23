package com.shadowwwbyte.smartexpense;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ExpenseManager {
    private List<Person> people = new ArrayList<>();
    private List<Bill> bills = new ArrayList<>();

    public List<Person> getPeople() { return people; }
    public List<Bill> getBills() { return bills; }

    public Person addPerson(String name) {
        Person p = new Person(name); people.add(p); saveToFileCached(null); return p;
    }
    public void removePerson(Person p) { people.remove(p); saveToFileCached(null); }
    public void updatePerson(Person p, String newName) { p.setName(newName); saveToFileCached(null); }

    public Bill addBill(String title, String desc, double total, int payerId) {
        Bill b = new Bill(title, desc, total, payerId); bills.add(b); saveToFileCached(null); return b;
    }
    public void removeBill(Bill b) { bills.remove(b); saveToFileCached(null); }
    public void updateBill(Bill b, String title, String desc, double total, int payerId) {
        b.setTitle(title); b.setDescription(desc); b.setTotal(total); b.setPayerId(payerId);
        saveToFileCached(null);
    }

    public Person getPersonById(int id) { for (Person p : people) if (p.getId() == id) return p; return null; }

    public Map<Integer, Double> computeBalances() {
        Map<Integer, Double> bal = new HashMap<>();
        for (Person p : people) bal.put(p.getId(), 0.0);
        for (Bill b : bills) {
            if (b.hasIndividualAmounts()) {
                bal.put(b.getPayerId(), bal.getOrDefault(b.getPayerId(), 0.0) + b.getTotal());
                for (Map.Entry<Integer, Double> e : b.getIndividualAmounts().entrySet())
                    bal.put(e.getKey(), bal.getOrDefault(e.getKey(), 0.0) - e.getValue());
            } else {
                int n = people.size(); if (n == 0) continue;
                double share = Math.round((b.getTotal() / n) * 100.0) / 100.0;
                for (Person p : people) bal.put(p.getId(), bal.getOrDefault(p.getId(), 0.0) - share);
                bal.put(b.getPayerId(), bal.getOrDefault(b.getPayerId(), 0.0) + b.getTotal());
            }
        }
        Map<Integer, Double> rounded = new HashMap<>();
        for (Map.Entry<Integer, Double> e : bal.entrySet())
            rounded.put(e.getKey(), Math.round(e.getValue() * 100.0) / 100.0);
        return rounded;
    }

    public List<String> settleMinimal() {
        Map<Integer, Double> bal = computeBalances();
        Map<Integer, Person> idToPerson = people.stream().collect(Collectors.toMap(Person::getId, p -> p));
        List<Map.Entry<Integer, Double>> creditors = new ArrayList<>(), debtors = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : bal.entrySet()) {
            if (!idToPerson.containsKey(e.getKey())) continue; // skip orphaned IDs
            if (e.getValue() > 0.009) creditors.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
            else if (e.getValue() < -0.009) debtors.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
        }
        creditors.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        debtors.sort(Comparator.comparingDouble(Map.Entry::getValue));
        List<String> tx = new ArrayList<>();
        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            var d = debtors.get(i); var c = creditors.get(j);
            double owe = -d.getValue(), claim = c.getValue(); double pay = Math.min(owe, claim);
            pay = Math.round(pay * 100.0) / 100.0;
            if (pay > 0) {
                Person debtor = idToPerson.get(d.getKey());
                Person creditor = idToPerson.get(c.getKey());
                if (debtor == null || creditor == null) { i++; j++; continue; }
                tx.add(debtor.getName() + " pays " + creditor.getName() + " " + String.format("%.2f", pay));
                d.setValue(d.getValue() + pay); c.setValue(c.getValue() - pay);
            }
            if (Math.abs(d.getValue()) < 0.01) i++;
            if (Math.abs(c.getValue()) < 0.01) j++;
        }
        return tx;
    }

    public double getTotalExpenses() {
        double total = 0; for (Bill b : bills) total += b.getTotal(); return total;
    }

    // --- Persistence ---
    private Context cachedCtx = null;
    public void setContextForPersistence(Context ctx) { this.cachedCtx = ctx; }
    private void saveToFileCached(Context ctx) { saveToFile(ctx == null ? cachedCtx : ctx); }

    public void saveToFile(Context ctx) {
        if (ctx == null) return;
        try {
            JSONObject root = new JSONObject();
            JSONArray pa = new JSONArray();
            for (Person p : people) { JSONObject jo = new JSONObject(); jo.put("id", p.getId()); jo.put("name", p.getName()); pa.put(jo); }
            root.put("people", pa);
            JSONArray ba = new JSONArray();
            for (Bill b : bills) {
                JSONObject bo = new JSONObject();
                bo.put("title", b.getTitle()); bo.put("description", b.getDescription());
                bo.put("total", b.getTotal()); bo.put("payerId", b.getPayerId());
                JSONObject ind = new JSONObject();
                for (Map.Entry<Integer, Double> e : b.getIndividualAmounts().entrySet()) ind.put(Integer.toString(e.getKey()), e.getValue());
                bo.put("individual", ind); ba.put(bo);
            }
            root.put("bills", ba);
            File f = new File(ctx.getFilesDir(), getFileName());
            try (FileWriter fw = new FileWriter(f)) { fw.write(root.toString(2)); }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void loadFromFile(Context ctx) {
        if (ctx == null) return;
        try {
            File f = new File(ctx.getFilesDir(), getFileName());
            if (!f.exists()) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            JSONObject root = new JSONObject(sb.toString());
            people.clear(); bills.clear();
            JSONArray pa = root.optJSONArray("people");
            if (pa != null) for (int i = 0; i < pa.length(); i++) { JSONObject jo = pa.getJSONObject(i); addPerson(jo.optString("name", "")); }
            JSONArray ba = root.optJSONArray("bills");
            if (ba != null) for (int i = 0; i < ba.length(); i++) {
                JSONObject bo = ba.getJSONObject(i);
                Bill b = addBill(bo.optString("title", "Bill"), bo.optString("description", ""), bo.optDouble("total", 0.0), bo.optInt("payerId", -1));
                JSONObject ind = bo.optJSONObject("individual");
                if (ind != null) { Iterator<String> keys = ind.keys(); while (keys.hasNext()) { String k = keys.next(); try { b.setIndividualAmount(Integer.parseInt(k), ind.optDouble(k, 0.0)); } catch (Exception ex) {} } }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    // --- Group file routing ---
    private int groupId = -1;
    public void setGroupId(int id) { this.groupId = id; }
    public int getGroupId() { return groupId; }
    private String getFileName() { return groupId >= 0 ? "group_" + groupId + ".json" : "data.json"; }
}
