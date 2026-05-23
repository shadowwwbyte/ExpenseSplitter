package com.shadowwwbyte.smartexpense;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.Map;

public class AddBillDialog {
    private final Context ctx;
    private final ExpenseManager manager;
    private final ArrayAdapter<Bill> adapter;
    private final Bill existing; // null = add, non-null = edit

    public AddBillDialog(Context ctx, ExpenseManager manager, ArrayAdapter<Bill> adapter, Bill existing) {
        this.ctx = ctx; this.manager = manager; this.adapter = adapter; this.existing = existing;
    }

    public void show() {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_bill, null);
        EditText titleEdit = view.findViewById(R.id.edit_title);
        EditText descEdit = view.findViewById(R.id.edit_desc);
        EditText totalEdit = view.findViewById(R.id.edit_total);
        Spinner payerSpinner = view.findViewById(R.id.spinner_payer);
        Spinner modeSpinner = view.findViewById(R.id.spinner_mode);
        LinearLayout indivContainer = view.findViewById(R.id.indiv_container);

        List<Person> people = manager.getPeople();
        ArrayAdapter<Person> payerAdapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, people);
        payerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payerSpinner.setAdapter(payerAdapter);

        String[] modes = {"Split evenly", "Individual amounts"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);

        // Pre-fill for editing
        if (existing != null) {
            titleEdit.setText(existing.getTitle());
            descEdit.setText(existing.getDescription());
            totalEdit.setText(String.format("%.2f", existing.getTotal()));
            for (int i = 0; i < people.size(); i++) {
                if (people.get(i).getId() == existing.getPayerId()) { payerSpinner.setSelection(i); break; }
            }
            if (existing.hasIndividualAmounts()) modeSpinner.setSelection(1);
        }

        EditText[] indivEdits = new EditText[people.size()];
        for (int i = 0; i < people.size(); i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView lbl = new TextView(ctx); lbl.setText(people.get(i).getName() + ": ");
            lbl.setPadding(0, 8, 8, 0);
            // 0x2003 = numberDecimal|text, same as Total Amount field — gives numpad with + key
            EditText et = new EditText(ctx); et.setRawInputType(0x2003);
            et.setHint("e.g. 50+30"); et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (existing != null && existing.hasIndividualAmounts()) {
                Double amt = existing.getIndividualAmounts().get(people.get(i).getId());
                if (amt != null) et.setText(String.format("%.2f", amt));
            }
            indivEdits[i] = et;
            row.addView(lbl); row.addView(et);
            indivContainer.addView(row);
        }

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                indivContainer.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                totalEdit.setEnabled(pos == 0);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        if (existing != null && existing.hasIndividualAmounts()) {
            indivContainer.setVisibility(View.VISIBLE);
            totalEdit.setEnabled(false);
        }

        new AlertDialog.Builder(ctx)
            .setTitle(existing == null ? "Add Bill" : "Edit Bill")
            .setView(view)
            .setPositiveButton(existing == null ? "Add" : "Save", (d, w) -> {
                String title = titleEdit.getText().toString().trim();
                String desc = descEdit.getText().toString().trim();
                if (title.isEmpty()) { Toast.makeText(ctx, "Title required", Toast.LENGTH_SHORT).show(); return; }
                Person payer = (Person) payerSpinner.getSelectedItem();
                if (payer == null) { Toast.makeText(ctx, "Select a payer", Toast.LENGTH_SHORT).show(); return; }
                int mode = modeSpinner.getSelectedItemPosition();
                if (mode == 1) {
                    // Individual
                    double sum = 0;
                    double[] amounts = new double[people.size()];
                    for (int i = 0; i < indivEdits.length; i++) {
                        String s = indivEdits[i].getText().toString().trim();
                        try { amounts[i] = s.isEmpty() ? 0 : eval(s); } catch (Exception e) { Toast.makeText(ctx, "Invalid amount for " + people.get(i).getName(), Toast.LENGTH_SHORT).show(); return; }
                        sum += amounts[i];
                    }
                    if (existing == null) {
                        Bill b = manager.addBill(title, desc, sum, payer.getId());
                        for (int i = 0; i < people.size(); i++) if (amounts[i] > 0) b.setIndividualAmount(people.get(i).getId(), amounts[i]);
                    } else {
                        existing.clearIndividualAmounts();
                        manager.updateBill(existing, title, desc, sum, payer.getId());
                        for (int i = 0; i < people.size(); i++) if (amounts[i] > 0) existing.setIndividualAmount(people.get(i).getId(), amounts[i]);
                        manager.saveToFile(ctx);
                    }
                } else {
                    String ts = totalEdit.getText().toString().trim();
                    double total = 0;
                    try { total = eval(ts); } catch (Exception e) { Toast.makeText(ctx, "Invalid total", Toast.LENGTH_SHORT).show(); return; }
                    if (existing == null) {
                        manager.addBill(title, desc, total, payer.getId());
                    } else {
                        existing.clearIndividualAmounts();
                        manager.updateBill(existing, title, desc, total, payer.getId());
                    }
                }
                adapter.notifyDataSetChanged();
                manager.saveToFile(ctx);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private double eval(String s) {
        s = s.trim();
        // simple sum evaluator for expressions like "50+30"
        String[] parts = s.split("\\+");
        double sum = 0;
        for (String p : parts) sum += Double.parseDouble(p.trim());
        return sum;
    }
}
