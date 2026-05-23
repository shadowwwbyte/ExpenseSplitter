package com.shadowwwbyte.smartexpense;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

public class GroupManager {
    private static GroupManager instance;
    private List<Group> groups = new ArrayList<>();

    private GroupManager() {}

    public static GroupManager getInstance() {
        if (instance == null) instance = new GroupManager();
        return instance;
    }

    public List<Group> getGroups() { return groups; }

    public Group addGroup(String name, String description) {
        Group g = new Group(name, description);
        groups.add(g);
        return g;
    }

    public void removeGroup(Context ctx, Group g) {
        // Delete persisted data file
        if (ctx != null) {
            File f = new File(ctx.getFilesDir(), "group_" + g.getId() + ".json");
            if (f.exists()) f.delete();
        }
        groups.remove(g);
    }

    public void updateGroup(Group g, String name, String description) {
        g.setName(name); g.setDescription(description);
    }

    public Group getGroupById(int id) {
        for (Group g : groups) if (g.getId() == id) return g;
        return null;
    }

    public void saveToFile(Context ctx) {
        if (ctx == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) {
                JSONObject jo = new JSONObject();
                jo.put("id", g.getId());
                jo.put("name", g.getName());
                jo.put("description", g.getDescription());
                jo.put("createdAt", g.getCreatedAt());
                arr.put(jo);
            }
            File f = new File(ctx.getFilesDir(), "groups.json");
            try (FileWriter fw = new FileWriter(f)) { fw.write(arr.toString(2)); }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public void loadFromFile(Context ctx) {
        if (ctx == null) return;
        try {
            File f = new File(ctx.getFilesDir(), "groups.json");
            if (!f.exists()) return;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line; while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            JSONArray arr = new JSONArray(sb.toString());
            groups.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject jo = arr.getJSONObject(i);
                Group g = new Group(jo.optString("name", "Group"), jo.optString("description", ""));
                g.setCreatedAt(jo.optLong("createdAt", System.currentTimeMillis()));
                groups.add(g);
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
