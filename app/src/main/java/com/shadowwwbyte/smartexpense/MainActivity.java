package com.shadowwwbyte.smartexpense;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

public class MainActivity extends AppCompatActivity {
    ExpenseManager manager = new ExpenseManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int groupId = getIntent().getIntExtra("group_id", -1);
        String groupName = getIntent().getStringExtra("group_name");

        manager.setGroupId(groupId);
        manager.setContextForPersistence(this);
        manager.loadFromFile(this);

        // Show group name in toolbar
        TextView title = findViewById(R.id.toolbar_title);
        if (title != null) title.setText(groupName != null ? groupName : "Expense Splitter");

        FragmentManager fm = getSupportFragmentManager();
        PeopleFragment peopleFrag = new PeopleFragment(manager);
        BillsFragment billsFrag = new BillsFragment(manager);
        DashboardFragment dashFrag = new DashboardFragment(manager);

        fm.beginTransaction().replace(R.id.fragment_container, peopleFrag).commit();

        Button p = findViewById(R.id.btn_nav_people);
        Button b = findViewById(R.id.btn_nav_bills);
        Button d = findViewById(R.id.btn_nav_dashboard);

        p.setOnClickListener(v -> fm.beginTransaction().replace(R.id.fragment_container, peopleFrag).commit());
        b.setOnClickListener(v -> fm.beginTransaction().replace(R.id.fragment_container, billsFrag).commit());
        d.setOnClickListener(v -> { dashFrag.refresh(); fm.beginTransaction().replace(R.id.fragment_container, dashFrag).commit(); });

        // Back arrow
        Button back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());
    }
}
