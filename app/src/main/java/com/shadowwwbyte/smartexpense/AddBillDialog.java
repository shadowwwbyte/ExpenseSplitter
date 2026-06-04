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

    private static final int FG     = 0xFFEBDBB2;
    private static final int FG2    = 0xFFD5C4A1;
    private static final int BG3    = 0xFF665C54;
    private static final int YELLOW = 0xFFD79921;

    public AddBillDialog(Context ctx, ExpenseManager manager, ArrayAdapter<Bill> adapter, Bill existing) {
        this.ctx = ctx; this.manager = manager; this.adapter = adapter; this.existing = existing;
    }

    public void show() {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_add_bill, null);
        EditText titleEdit    = view.findViewById(R.id.edit_title);
        EditText descEdit     = view.findViewById(R.id.edit_desc);
        EditText totalEdit    = view.findViewById(R.id.edit_total);
        Spinner  payerSpinner = view.findViewById(R.id.spinner_payer);
        Spinner  modeSpinner  = view.findViewById(R.id.spinner_mode);
        LinearLayout indivContainer     = view.findViewById(R.id.indiv_container);
        LinearLayout multiPayerContainer = view.findViewById(R.id.multi_payer_container);

        List<Person> people = manager.getPeople();

        // ---- Payer spinner: people + "More than 1 contributed" ----
        List<String> payerLabels = new ArrayList<>();
        for (Person p : people) payerLabels.add(p.getName());
        payerLabels.add("More than 1 contributed");
        ArrayAdapter<String> payerAdapter = new ArrayAdapter<>(ctx,
            android.R.layout.simple_spinner_item, payerLabels);
        payerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        payerSpinner.setAdapter(payerAdapter);

        // ---- Split mode spinner ----
        String[] modes = {"Split evenly", "Individual amounts"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(ctx,
            android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);

        // ---- Build multi-payer contribution rows (checkbox + amount field) ----
        // These appear between Payer and Split Mode
        CheckBox[] mpChecks = new CheckBox[people.size()];
        EditText[] mpEdits  = new EditText[people.size()];
        for (int i = 0; i < people.size(); i++) {
            // One row: [CheckBox "Name"]  [EditText amount]
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 6, 0, 2);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            CheckBox cb = new CheckBox(ctx);
            cb.setText(people.get(i).getName());
            cb.setTextColor(FG);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(YELLOW));
            cb.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mpChecks[i] = cb;

            EditText et = makeNumEdit("amount paid");
            et.setVisibility(View.GONE);
            et.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mpEdits[i] = et;

            final EditText finalEt = et;
            cb.setOnCheckedChangeListener((btn, checked) ->
                finalEt.setVisibility(checked ? View.VISIBLE : View.GONE));

            row.addView(cb);
            row.addView(et);
            multiPayerContainer.addView(row);

            // Pre-fill if editing
            if (existing != null && existing.isMultiPayer()) {
                Double contrib = existing.getMultiPayers().get(people.get(i).getId());
                if (contrib != null && contrib > 0) {
                    cb.setChecked(true);
                    et.setText(String.format("%.2f", contrib));
                    et.setVisibility(View.VISIBLE);
                }
            }
        }

        // ---- Build individual-owed rows (below Split Mode, unchanged) ----
        EditText[] indivEdits = new EditText[people.size()];
        for (int i = 0; i < people.size(); i++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 4, 0, 4);
            TextView lbl = new TextView(ctx);
            lbl.setText(people.get(i).getName() + ": ");
            lbl.setPadding(0, 10, 8, 0);
            lbl.setTextColor(FG2);
            EditText et = makeNumEdit("e.g. 50+30");
            et.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (existing != null && existing.hasIndividualAmounts()) {
                Double amt = existing.getIndividualAmounts().get(people.get(i).getId());
                if (amt != null) et.setText(String.format("%.2f", amt));
            }
            indivEdits[i] = et;
            row.addView(lbl);
            row.addView(et);
            indivContainer.addView(row);
        }

        // ---- Pre-fill fields when editing ----
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
            if (existing.hasIndividualAmounts()) {
                modeSpinner.setSelection(1);
                indivContainer.setVisibility(View.VISIBLE);
                totalEdit.setEnabled(false);
            }
        }

        // ---- Payer spinner listener ----
        payerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                boolean multi = (pos == people.size());
                multiPayerContainer.setVisibility(multi ? View.VISIBLE : View.GONE);
                // When multi-payer, total is auto-summed from contributions — disable manual entry
                if (multi) {
                    totalEdit.setEnabled(false);
                    totalEdit.setHint("Auto from contributions");
                } else {
                    // Re-enable only if split evenly
                    totalEdit.setEnabled(modeSpinner.getSelectedItemPosition() == 0);
                    totalEdit.setHint("e.g. 100 or 50+50");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // ---- Split mode listener ----
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                boolean isIndiv = (pos == 1);
                indivContainer.setVisibility(isIndiv ? View.VISIBLE : View.GONE);
                boolean isMulti = (payerSpinner.getSelectedItemPosition() == people.size());
                // Total field enabled only for single-payer + split evenly
                totalEdit.setEnabled(!isIndiv && !isMulti);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

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

                // --- Collect multi-payer contributions ---
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

                // --- Collect individual owed amounts ---
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

                // --- Validate contribution vs individual mismatch ---
                if (isMulti && mode == 1 && contribTotal > 0 && indivSum > 0) {
                    double diff = Math.round(Math.abs(contribTotal - indivSum) * 100.0) / 100.0;
                    if (diff >= 0.01) {
                        String msg = String.format(
                            "Mismatch: Contributions total (%.2f) \u2260 Individual expenses total (%.2f).\nDifference: %.2f. Please correct and try again.",
                            contribTotal, indivSum, diff);
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                // --- Determine total ---
                double total;
                if (isMulti) {
                    total = (mode == 1) ? indivSum : contribTotal;
                } else if (mode == 1) {
                    total = indivSum;
                } else {
                    String ts = totalEdit.getText().toString().trim();
                    try { total = eval(ts); }
                    catch (Exception e) {
                        Toast.makeText(ctx, "Invalid total", Toast.LENGTH_SHORT).show(); return;
                    }
                }

                int singlePayerId = isMulti ? -1 :
                    people.get(payerSpinner.getSelectedItemPosition()).getId();

                // --- Apply to bill (single save at end) ---
                Bill b;
                if (existing == null) {
                    b = manager.addBillNoSave(title, desc, total, singlePayerId);
                } else {
                    b = existing;
                    b.clearIndividualAmounts();
                    b.clearMultiPayers();
                    manager.updateBillNoSave(b, title, desc, total, singlePayerId);
                }

                if (isMulti) {
                    for (Map.Entry<Integer, Double> e : contributions.entrySet())
                        b.setMultiPayer(e.getKey(), e.getValue());
                }

                if (mode == 1) {
                    for (int i = 0; i < people.size(); i++)
                        if (indivAmounts[i] > 0)
                            b.setIndividualAmount(people.get(i).getId(), indivAmounts[i]);
                }

                adapter.notifyDataSetChanged();
                manager.saveToFile(ctx);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    /** Number-pad EditText with gruvbox styling */
    private EditText makeNumEdit(String hint) {
        EditText et = new EditText(ctx);
        et.setRawInputType(0x2003); // numberDecimal|text — numpad with +
        et.setHint(hint);
        et.setHintTextColor(BG3);
        et.setTextColor(FG);
        et.getBackground().setTint(YELLOW);
        return et;
    }

    /**
     * Evaluates arithmetic expressions with +, -, *, /
     * e.g. "25+55/3", "100*0.18/4+30", "50+30-10"
     */
    private double eval(String s) {
        return new ExprParser(s.trim()).parse();
    }

    private static class ExprParser {
        private final String expr;
        private int pos;

        ExprParser(String expr) { this.expr = expr; this.pos = 0; }

        double parse() {
            double result = parseTerm();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '+' || op == '-') {
                    pos++;
                    double t = parseTerm();
                    result = (op == '+') ? result + t : result - t;
                } else break;
            }
            return result;
        }

        private double parseTerm() {
            double result = parseFactor();
            while (pos < expr.length()) {
                char op = expr.charAt(pos);
                if (op == '*' || op == '/') {
                    pos++;
                    double f = parseFactor();
                    result = (op == '*') ? result * f : result / f;
                } else break;
            }
            return result;
        }

        private double parseFactor() {
            skipSpaces();
            if (pos < expr.length() && expr.charAt(pos) == '(') {
                pos++; // consume '('
                double val = parse();
                if (pos < expr.length() && expr.charAt(pos) == ')') pos++;
                return val;
            }
            int start = pos;
            if (pos < expr.length() && expr.charAt(pos) == '-') pos++; // unary minus
            while (pos < expr.length() && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) pos++;
            return Double.parseDouble(expr.substring(start, pos));
        }

        private void skipSpaces() {
            while (pos < expr.length() && expr.charAt(pos) == ' ') pos++;
        }
    }
}
