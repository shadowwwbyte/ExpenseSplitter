package com.shadowwwbyte.smartexpense;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class PeopleFragment extends Fragment {
    private ExpenseManager manager;
    private ArrayAdapter<Person> adapter;

    public PeopleFragment(ExpenseManager manager) { this.manager = manager; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_people, container, false);
        ListView list = root.findViewById(R.id.people_list);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_single_choice, manager.getPeople());
        list.setAdapter(adapter);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        Button add = root.findViewById(R.id.btn_add_person);
        Button edit = root.findViewById(R.id.btn_edit_person);
        Button remove = root.findViewById(R.id.btn_remove_person);

        add.setOnClickListener(v -> showPersonDialog(null, list));
        edit.setOnClickListener(v -> {
            int pos = list.getCheckedItemPosition();
            if (pos < 0) { Toast.makeText(getContext(), "Select a person to edit", Toast.LENGTH_SHORT).show(); return; }
            showPersonDialog(adapter.getItem(pos), list);
        });
        remove.setOnClickListener(v -> {
            int pos = list.getCheckedItemPosition();
            if (pos >= 0) {
                Person sel = adapter.getItem(pos);
                if (sel != null) {
                    new AlertDialog.Builder(getContext())
                        .setTitle("Remove Person")
                        .setMessage("Remove " + sel.getName() + "?")
                        .setPositiveButton("Remove", (d, w) -> {
                            manager.removePerson(sel);
                            manager.saveToFile(getContext());
                            adapter.notifyDataSetChanged();
                        })
                        .setNegativeButton("Cancel", null).show();
                }
            } else Toast.makeText(getContext(), "Select a person", Toast.LENGTH_SHORT).show();
        });

        return root;
    }

    private void showPersonDialog(Person existing, ListView list) {
        EditText input = new EditText(getContext());
        if (existing != null) { input.setText(existing.getName()); input.setSelection(existing.getName().length()); }
        new AlertDialog.Builder(getContext())
            .setTitle(existing == null ? "Add Person" : "Edit Person")
            .setView(input)
            .setPositiveButton(existing == null ? "Add" : "Save", (d, w) -> {
                String n = input.getText().toString().trim();
                if (!n.isEmpty()) {
                    if (existing == null) { manager.addPerson(n); }
                    else { manager.updatePerson(existing, n); }
                    manager.saveToFile(getContext());
                    adapter.notifyDataSetChanged();
                    list.clearChoices();
                }
            })
            .setNegativeButton("Cancel", null).show();
    }
}
