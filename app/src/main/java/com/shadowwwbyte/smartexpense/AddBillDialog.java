package com.shadowwwbyte.smartexpense;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.*;

public class AddBillDialog {
    private final Context ctx;
    private final ExpenseManager manager;
    private final ArrayAdapter<Bill> adapter;
    private final Bill existing;

    // Gruvbox colours (inline so no resource lookup needed)
    private static final int FG       = 0xFFEBDBB2;
    private static final int FG2      = 0xFFD5C4A1;
    private static final int BG1      = 0xFF3C3836;
    private static final int BG3      = 0xFF665C54;
    private static final int YELLOW   = 0xFFD79921;
    private static final int BRIGHT_R = 0xFFFB4934;

    public AddBillDialog(Context ctx, ExpenseManager manager, ArrayAdapter<Bill> adapter, Bill existing) {
        this.ctx = ctx; this.manager = manager; this.adapter = adapter; this.existing = existing;
    }

    public void show() {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_bill, null);
        EditText titleEdit   = view.findViewById(R.id.edit_title);
        EditText descEdit    = view.findViewById(R.id.edit_desc);
        EditText totalEdit   = view.findViewById(R.id.edit_total);
        Spinner  payerSpinner = view.findViewById(R.id.spinner_payer);
        Spinner  modeSpinner  = view.findViewById(R.id.spinner_mode);
        LinearLayout indivContainer      = view.findViewById(R.id.indiv_container);
        LinearLayout multiPayerContainer = view.findViewById(R.id.multi_payer_container);

        List<Person> people = manager.getPeople();

        // ---- Payer spinner: people + "More than 1 contributed" ----
        List<String> payerLabels = new ArrayList<>();
        for (Person p : people) payerLabels.add(p.getName());
        payerLabels.add("More than 1 contributed");
        ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, payerLabels);
        payerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payerSpinner.setAdapter(payerAdapter);

        // ---- Split mode spinner ----
        String[] modes = {"Split evenly", "Individual amounts"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);

        // ---- Build individual-owed rows ----
        EditText[] indivEdits = new EditText[people.size()];
        for (int i = 0; i < people.size(); i++) {
            LinearLayout row = makeRow(people.get(i).getName() + ": ", "e.g. 50+30");
            indivEdits[i] = (EditText) row.getChildAt(1);
            if (existing != null && existing.hasIndividualAmounts()) {
                Double amt = existing.getIndividualAmounts().get(people.get(i).getId());
                if (amt != null) indivEdits[i].setText(String.format("%.2f", amt));
            }
            indivContainer.addView(row);
        }

        // ---- Build multi-payer contribution rows (checkbox + amount) ----
        CheckBox[]  mpChecks = new CheckBox[people.size()];
        EditText[]  mpEdits  = new EditText[people.size()];
        LinearLayout[] mpRows = new LinearLayout[people.size()];
        for (int i = 0; i < people.size(); i++) {
            // Checkbox row
            LinearLayout checkRow = new LinearLayout(ctx);
            checkRow.setOrientation(LinearLayout.HORIZONTAL);
            checkRow.setPadding(0, 6, 0, 2);
            CheckBox cb = new CheckBox(ctx);
            cb.setText(people.get(i).getName());
            cb.setTextColor(FG);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(YELLOW));
            mpChecks[i] = cb;
            checkRow.addView(cb);
            multiPayerContainer.addView(checkRow);

            // Amount row (hidden until checkbox ticked)
            LinearLayout amtRow = makeRow("  Amount paid: ", "e.g. 100");
            amtRow.setVisibility(View.GONE);
            mpEdits[i] = (EditText) amtRow.getChildAt(1);
            mpRows[i] = amtRow;
            multiPayerContainer.addView(amtRow);

            final int idx = i;
            cb.setOnCheckedChangeListener((btn, checked) ->
                amtRow.setVisibility(checked ? View.VISIBLE : View.GONE));

            // Pre-fill if editing a multi-payer bill
            if (existing != null && existing.isMultiPayer()) {
                Double contrib = existing.getMultiPayers().get(people.get(i).getId());
                if (contrib != null && contrib > 0) {
                    cb.setChecked(true);
                    mpEdits[idx].setText(String.format("%.2f", contrib));
                    amtRow.setVisibility(View.VISIBLE);
                }
            }
        }

        // ---- Pre-fill fields for editing ----
        if (existing != null) {
            titleEdit.setText(existing.getTitle());
            descEdit.setText(existing.getDescription());
            totalEdit.setText(String.format("%.2f", existing.getTotal()));
            if (existing.isMultiPayer()) {
                payerSpinner.setSelection(people.size()); // "More than 1 contributed"
                multiPayerContainer.setVisibility(View.VISIBLE);
            } else {
                for (int i = 0; i < people.size(); i++) {
                    if (people.get(i).getId() == existing.getPayerId()) {
                        payerSpinner.setSelection(i); break;
                    }
                }
            }
            if (existing.hasIndividualAmounts()) modeSpinner.setSelection(1);
        }

        // ---- Payer spinner listener ----
        payerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                boolean multi = (pos == people.size());
                multiPayerContainer.setVisibility(multi ? View.VISIBLE : View.GONE);
                totalEdit.setEnabled(!multi); // auto-sum from contributions when multi
                if (multi) totalEdit.setHint("Auto-calculated");
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // ---- Split mode listener ----
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                indivContainer.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                // totalEdit enabled only when single payer + even split
                boolean multi = (payerSpinner.getSelectedItemPosition() == people.size());
                totalEdit.setEnabled(pos == 0 && !multi);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Apply initial visibility
        if (existing != null && existing.hasIndividualAmounts()) {
            indivContainer.setVisibility(View.VISIBLE);
            totalEdit.setEnabled(false);
        }

        // ---- Build & show dialog ----
        new AlertDialog.Builder(ctx)
            .setTitle(existing == null ? "Add Bill" : "Edit Bill")
            .setView(view)
            .setPositiveButton(existing == null ? "Add" : "Save", (d, w) -> {
                String title = titleEdit.getText().toString().trim();
                String desc  = descEdit.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(ctx, "Title required", Toast.LENGTH_SHORT).show(); return;
                }

                boolean isMulti = (payerSpinner.getSelectedItemPosition() == people.size());
                int mode = modeSpinner.getSelectedItemPosition();

                // --- Validate & collect multi-payer contributions ---
                Map<Integer, Double> contributions = new LinkedHashMap<>();
                double contribTotal = 0;
                if (isMulti) {
                    boolean anyChecked = false;
                    for (int i = 0; i < people.size(); i++) {
                        if (mpChecks[i].isChecked()) {
                            anyChecked = true;
                            String s = mpEdits[i].getText().toString().trim();
                            double amt;
                            try { amt = s.isEmpty() ? 0 : eval(s); }
                            catch (Exception e) {
                                Toast.makeText(ctx, "Invalid amount for " + people.get(i).getName(), Toast.LENGTH_SHORT).show(); return;
                            }
                            if (amt <= 0) {
                                Toast.makeText(ctx, "Enter amount for " + people.get(i).getName(), Toast.LENGTH_SHORT).show(); return;
                            }
                            contributions.put(people.get(i).getId(), amt);
                            contribTotal += amt;
                        }
                    }
                    if (!anyChecked) {
                        Toast.makeText(ctx, "Select at least one contributor", Toast.LENGTH_SHORT).show(); return;
                    }
                }

                // --- Validate & collect individual owed amounts ---
                double[] indivAmounts = new double[people.size()];
                double indivSum = 0;
                if (mode == 1) {
                    for (int i = 0; i < indivEdits.length; i++) {
                        String s = indivEdits[i].getText().toString().trim();
                        try { indivAmounts[i] = s.isEmpty() ? 0 : eval(s); }
                        catch (Exception e) {
                            Toast.makeText(ctx, "Invalid amount for " + people.get(i).getName(), Toast.LENGTH_SHORT).show(); return;
                        }
                        indivSum += indivAmounts[i];
                    }
                }

                // --- Determine total ---
                double total;
                if (isMulti) {
                    total = contribTotal; // total = sum of contributions
                } else if (mode == 1) {
                    total = indivSum;
                } else {
                    String ts = totalEdit.getText().toString().trim();
                    try { total = eval(ts); }
                    catch (Exception e) { Toast.makeText(ctx, "Invalid total", Toast.LENGTH_SHORT).show(); return; }
                }

                // --- Apply to bill (no save until fully set) ---
                Bill b;
                if (existing == null) {
                    b = manager.addBillNoSave(title, desc, total, isMulti ? -1 : people.get(payerSpinner.getSelectedItemPosition()).getId());
                } else {
                    b = existing;
                    b.clearIndividualAmounts();
                    b.clearMultiPayers();
                    manager.updateBillNoSave(b, title, desc, total, isMulti ? -1 : people.get(payerSpinner.getSelectedItemPosition()).getId());
                }

                // Set multi-payer contributions
                if (isMulti) {
                    for (Map.Entry<Integer, Double> e : contributions.entrySet())
                        b.setMultiPayer(e.getKey(), e.getValue());
                }

                // Set individual owed amounts
                if (mode == 1) {
                    for (int i = 0; i < people.size(); i++)
                        if (indivAmounts[i] > 0) b.setIndividualAmount(people.get(i).getId(), indivAmounts[i]);
                }

                adapter.notifyDataSetChanged();
                manager.saveToFile(ctx);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Creates a label + numpad EditText row */
    private LinearLayout makeRow(String label, String hint) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 4, 0, 4);
        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setPadding(0, 10, 8, 0);
        lbl.setTextColor(FG2);
        EditText et = new EditText(ctx);
        et.setRawInputType(0x2003); // numberDecimal|text — numpad with +
        et.setHint(hint);
        et.setHintTextColor(BG3);
        et.setTextColor(FG);
        et.getBackground().setTint(YELLOW);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(lbl);
        row.addView(et);
        return row;
    }

    private double eval(String s) {
        s = s.trim();
        String[] parts = s.split("\\+");
        double sum = 0;
        for (String p : parts) sum += Double.parseDouble(p.trim());
        return sum;
    }
}
