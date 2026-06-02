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

    // ---- People ----
    public Person addPerson(String name) {
        Person p = new Person(name); people.add(p); saveToFileCached(null); return p;
    }
    // Internal: restore a person with a known ID (used by loadFromFile only)
    private Person restorePerson(int id, String name) {
        Person p = new Person(name); p.forceId(id); people.add(p); return p;
    }
    public void removePerson(Person p) { people.remove(p); saveToFileCached(null); }
    public void updatePerson(Person p, String newName) { p.setName(newName); saveToFileCached(null); }

    // ---- Bills ----
    public Bill addBill(String title, String desc, double total, int payerId) {
        Bill b = new Bill(title, desc, total, payerId); bills.add(b); saveToFileCached(null); return b;
    }
    // Add bill without triggering auto-save — caller must call saveToFile() after setting all amounts
    public Bill addBillNoSave(String title, String desc, double total, int payerId) {
        Bill b = new Bill(title, desc, total, payerId); bills.add(b); return b;
    }
    // Internal: restore a bill without triggering save (used by loadFromFile only)
    private Bill restoreBill(String title, String desc, double total, int payerId) {
        Bill b = new Bill(title, desc, total, payerId); bills.add(b); return b;
    }
    public void removeBill(Bill b) { bills.remove(b); saveToFileCached(null); }
    // Update bill fields without triggering auto-save — caller must call saveToFile() after
    public void updateBillNoSave(Bill b, String title, String desc, double total, int payerId) {
        b.setTitle(title); b.setDescription(desc); b.setTotal(total); b.setPayerId(payerId);
    }
    public void updateBill(Bill b, String title, String desc, double total, int payerId) {
        b.setTitle(title); b.setDescription(desc); b.setTotal(total); b.setPayerId(payerId);
        saveToFileCached(null);
    }

    public Person getPersonById(int id) {
        for (Person p : people) if (p.getId() == id) return p; return null;
    }

    // ---- Balance computation ----
    public Map<Integer, Double> computeBalances() {
        Map<Integer, Double> bal = new LinkedHashMap<>();
        for (Person p : people) bal.put(p.getId(), 0.0);

        for (Bill b : bills) {
            int n = people.size();
            if (n == 0) continue;

            if (b.hasIndividualAmounts()) {
                // Payer gets credited the full amount
                if (bal.containsKey(b.getPayerId()))
                    bal.put(b.getPayerId(), bal.get(b.getPayerId()) + b.getTotal());
                // Each person is debited their individual share
                for (Map.Entry<Integer, Double> e : b.getIndividualAmounts().entrySet()) {
                    if (bal.containsKey(e.getKey()))
                        bal.put(e.getKey(), bal.get(e.getKey()) - e.getValue());
                }
            } else {
                // Even split: distribute remainder to avoid float drift
                double total = b.getTotal();
                double baseShare = Math.floor((total / n) * 100.0) / 100.0;
                double remainder = Math.round((total - baseShare * n) * 100.0) / 100.0;
                int remCents = (int) Math.round(remainder * 100);

                List<Person> pList = people;
                for (int i = 0; i < pList.size(); i++) {
                    double share = baseShare + (i < remCents ? 0.01 : 0.00);
                    int pid = pList.get(i).getId();
                    bal.put(pid, bal.getOrDefault(pid, 0.0) - share);
                }
                if (bal.containsKey(b.getPayerId()))
                    bal.put(b.getPayerId(), bal.get(b.getPayerId()) + total);
            }
        }

        // Final rounding pass to clean up float noise
        Map<Integer, Double> rounded = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> e : bal.entrySet())
            rounded.put(e.getKey(), Math.round(e.getValue() * 100.0) / 100.0);
        return rounded;
    }

    // ---- Settlement (minimal transactions) ----
    public List<String> settleMinimal() {
        Map<Integer, Double> bal = computeBalances();
        Map<Integer, Person> idToPerson = people.stream()
            .collect(Collectors.toMap(Person::getId, p -> p));

        List<double[]> creditors = new ArrayList<>(); // [id, amount]
        List<double[]> debtors   = new ArrayList<>();

        for (Map.Entry<Integer, Double> e : bal.entrySet()) {
            if (!idToPerson.containsKey(e.getKey())) continue;
            double v = e.getValue();
            if      (v >  0.009) creditors.add(new double[]{e.getKey(), v});
            else if (v < -0.009) debtors.add(new double[]{e.getKey(), -v}); // store as positive
        }

        creditors.sort((a, b) -> Double.compare(b[1], a[1]));
        debtors.sort((a, b)   -> Double.compare(b[1], a[1]));

        List<String> tx = new ArrayList<>();
        int i = 0, j = 0;
        int safetyLimit = (debtors.size() + creditors.size()) * 10 + 10;
        int iterations = 0;

        while (i < debtors.size() && j < creditors.size()) {
            if (++iterations > safetyLimit) break; // prevent any infinite loop

            double owe   = debtors.get(i)[1];
            double claim = creditors.get(j)[1];
            double pay   = Math.round(Math.min(owe, claim) * 100.0) / 100.0;

            if (pay >= 0.01) {
                int debtorId   = (int) debtors.get(i)[0];
                int creditorId = (int) creditors.get(j)[0];
                Person debtor   = idToPerson.get(debtorId);
                Person creditor = idToPerson.get(creditorId);
                if (debtor == null || creditor == null) { i++; j++; continue; }

                tx.add(debtor.getName() + " pays " + creditor.getName()
                    + "  " + String.format("%.2f", pay));

                debtors.get(i)[1]   = Math.round((owe   - pay) * 100.0) / 100.0;
                creditors.get(j)[1] = Math.round((claim - pay) * 100.0) / 100.0;
            }

            // Always advance at least one pointer to guarantee termination
            if (debtors.get(i)[1] < 0.01)   i++;
            else if (creditors.get(j)[1] < 0.01) j++;
            else { i++; j++; } // both had tiny residuals — skip both
        }
        return tx;
    }

    public double getTotalExpenses() {
        double t = 0; for (Bill b : bills) t += b.getTotal(); return t;
    }

    // ---- Persistence ----
    private Context cachedCtx = null;
    public void setContextForPersistence(Context ctx) { this.cachedCtx = ctx; }
    private void saveToFileCached(Context ctx) { saveToFile(ctx == null ? cachedCtx : ctx); }

    public void saveToFile(Context ctx) {
        if (ctx == null) return;
        try {
            JSONObject root = new JSONObject();
            JSONArray pa = new JSONArray();
            for (Person p : people) {
                JSONObject jo = new JSONObject();
                jo.put("id", p.getId());
                jo.put("name", p.getName());
                pa.put(jo);
            }
            root.put("people", pa);
            JSONArray ba = new JSONArray();
            for (Bill b : bills) {
                JSONObject bo = new JSONObject();
                bo.put("title", b.getTitle());
                bo.put("description", b.getDescription());
                bo.put("total", b.getTotal());
                bo.put("payerId", b.getPayerId());
                JSONObject ind = new JSONObject();
                for (Map.Entry<Integer, Double> e : b.getIndividualAmounts().entrySet())
                    ind.put(Integer.toString(e.getKey()), e.getValue());
                bo.put("individual", ind);
                ba.put(bo);
            }
            root.put("bills", ba);
            // Write to temp file first, then rename — prevents corruption on crash
            File dir = ctx.getFilesDir();
            File tmp = new File(dir, getFileName() + ".tmp");
            File fin = new File(dir, getFileName());
            try (FileWriter fw = new FileWriter(tmp)) { fw.write(root.toString(2)); }
            tmp.renameTo(fin);
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

            // Track max ID so Person.COUNTER stays ahead
            int maxId = 0;
            JSONArray pa = root.optJSONArray("people");
            if (pa != null) {
                for (int i = 0; i < pa.length(); i++) {
                    JSONObject jo = pa.getJSONObject(i);
                    int id = jo.optInt("id", -1);
                    String name = jo.optString("name", "");
                    if (id > 0 && !name.isEmpty()) {
                        restorePerson(id, name); // preserves original ID
                        if (id > maxId) maxId = id;
                    }
                }
            }
            Person.advanceCounter(maxId); // ensure new persons get fresh IDs

            JSONArray ba = root.optJSONArray("bills");
            if (ba != null) {
                for (int i = 0; i < ba.length(); i++) {
                    JSONObject bo = ba.getJSONObject(i);
                    Bill b = restoreBill(
                        bo.optString("title", "Bill"),
                        bo.optString("description", ""),
                        bo.optDouble("total", 0.0),
                        bo.optInt("payerId", -1)
                    );
                    JSONObject ind = bo.optJSONObject("individual");
                    if (ind != null) {
                        Iterator<String> keys = ind.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            try { b.setIndividualAmount(Integer.parseInt(k), ind.optDouble(k, 0.0)); }
                            catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    // ---- Group file routing ----
    private int groupId = -1;
    public void setGroupId(int id) { this.groupId = id; }
    public int getGroupId() { return groupId; }
    private String getFileName() { return groupId >= 0 ? "group_" + groupId + ".json" : "data.json"; }
}
