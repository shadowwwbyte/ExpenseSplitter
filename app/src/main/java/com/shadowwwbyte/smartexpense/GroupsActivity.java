package com.shadowwwbyte.smartexpense;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.*;

public class GroupsActivity extends AppCompatActivity {
    private GroupManager gm;
    private ListView listView;
    private ArrayAdapter<Group> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups);
        gm = GroupManager.getInstance();
        gm.loadFromFile(this);

        listView = findViewById(R.id.groups_list);
        adapter = new ArrayAdapter<Group>(this, R.layout.item_group, R.id.group_name, gm.getGroups()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null)
                    convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_group, parent, false);
                Group g = getItem(position);
                TextView name = convertView.findViewById(R.id.group_name);
                TextView desc = convertView.findViewById(R.id.group_desc);
                name.setText(g.getName());
                desc.setText(g.getDescription().isEmpty() ? "Tap to open" : g.getDescription());
                return convertView;
            }
        };
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Group g = adapter.getItem(position);
            openGroup(g);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Group g = adapter.getItem(position);
            showGroupOptions(g);
            return true;
        });

        findViewById(R.id.btn_add_group).setOnClickListener(v -> showAddGroupDialog(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    private void openGroup(Group g) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("group_id", g.getId());
        intent.putExtra("group_name", g.getName());
        startActivity(intent);
    }

    private void showAddGroupDialog(Group existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_group, null);
        EditText nameEdit = v.findViewById(R.id.edit_group_name);
        EditText descEdit = v.findViewById(R.id.edit_group_desc);
        if (existing != null) {
            nameEdit.setText(existing.getName());
            descEdit.setText(existing.getDescription());
        }
        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "New Group" : "Edit Group")
            .setView(v)
            .setPositiveButton(existing == null ? "Create" : "Save", (d, w) -> {
                String name = nameEdit.getText().toString().trim();
                String desc = descEdit.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show(); return; }
                if (existing == null) {
                    gm.addGroup(name, desc);
                } else {
                    gm.updateGroup(existing, name, desc);
                }
                gm.saveToFile(this);
                adapter.notifyDataSetChanged();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showGroupOptions(Group g) {
        String[] options = {"Open", "Edit", "Delete"};
        new AlertDialog.Builder(this)
            .setTitle(g.getName())
            .setItems(options, (d, which) -> {
                if (which == 0) openGroup(g);
                else if (which == 1) showAddGroupDialog(g);
                else {
                    new AlertDialog.Builder(this)
                        .setTitle("Delete Group")
                        .setMessage("Delete \"" + g.getName() + "\" and all its data?")
                        .setPositiveButton("Delete", (d2, w2) -> {
                            gm.removeGroup(this, g);
                            gm.saveToFile(this);
                            adapter.notifyDataSetChanged();
                        })
                        .setNegativeButton("Cancel", null).show();
                }
            }).show();
    }
}
