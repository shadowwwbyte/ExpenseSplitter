package com.shadowwwbyte.smartexpense;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.Map;

public class BillsFragment extends Fragment {
    private ExpenseManager manager;
    private ArrayAdapter<Bill> adapter;

    public BillsFragment(ExpenseManager manager) { this.manager = manager; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_bills, container, false);
        ListView list = root.findViewById(R.id.bills_list);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_single_choice, manager.getBills());
        list.setAdapter(adapter);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        Button add = root.findViewById(R.id.btn_add_bill);
        Button edit = root.findViewById(R.id.btn_edit_bill);
        Button view = root.findViewById(R.id.btn_view_bill);
        Button remove = root.findViewById(R.id.btn_remove_bill);

        add.setOnClickListener(v -> {
            AddBillDialog dlg = new AddBillDialog(getContext(), manager, adapter, null);
            dlg.show();
        });

        edit.setOnClickListener(v -> {
            int pos = list.getCheckedItemPosition();
            Bill b = (pos >= 0) ? adapter.getItem(pos) : null;
            if (b == null) { Toast.makeText(getContext(), "Select a bill to edit", Toast.LENGTH_SHORT).show(); return; }
            AddBillDialog dlg = new AddBillDialog(getContext(), manager, adapter, b);
            dlg.show();
        });

        view.setOnClickListener(v -> {
            int pos = list.getCheckedItemPosition();
            Bill b = (pos >= 0) ? adapter.getItem(pos) : null;
            if (b == null) { Toast.makeText(getContext(), "Select a bill", Toast.LENGTH_SHORT).show(); return; }
            StringBuilder sb = new StringBuilder();
            sb.append("Title: ").append(b.getTitle()).append("\n");
            sb.append("Description: ").append(b.getDescription()).append("\n");
            sb.append("Total: ").append(String.format("%.2f", b.getTotal())).append("\n");
            Person payer = manager.getPersonById(b.getPayerId());
            sb.append("Payer: ").append(payer != null ? payer.getName() : "Unknown").append("\n");
            if (b.hasIndividualAmounts()) {
                sb.append("\nIndividual amounts:\n");
                for (Map.Entry<Integer, Double> e2 : b.getIndividualAmounts().entrySet()) {
                    Person p = manager.getPersonById(e2.getKey());
                    sb.append(" - ").append(p != null ? p.getName() : ("id:" + e2.getKey())).append(": ").append(String.format("%.2f", e2.getValue())).append("\n");
                }
            }
            new AlertDialog.Builder(getContext()).setTitle("Bill Details").setMessage(sb.toString()).setPositiveButton("OK", null).show();
        });

        remove.setOnClickListener(v -> {
            int pos = list.getCheckedItemPosition();
            Bill b = (pos >= 0) ? adapter.getItem(pos) : null;
            if (b != null) {
                new AlertDialog.Builder(getContext())
                    .setTitle("Remove Bill")
                    .setMessage("Remove \"" + b.getTitle() + "\"?")
                    .setPositiveButton("Remove", (d, w) -> { manager.removeBill(b); manager.saveToFile(getContext()); adapter.notifyDataSetChanged(); })
                    .setNegativeButton("Cancel", null).show();
            } else Toast.makeText(getContext(), "Select a bill", Toast.LENGTH_SHORT).show();
        });

        return root;
    }
}
