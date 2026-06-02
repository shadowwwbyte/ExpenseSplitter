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
        if (ctx != null) {
            File f = new File(ctx.getFilesDir(), "group_" + g.getId() + ".json");
            if (f.exists()) f.delete();
            // also clean up any .tmp left over
            File tmp = new File(ctx.getFilesDir(), "group_" + g.getId() + ".json.tmp");
            if (tmp.exists()) tmp.delete();
        }
        groups.remove(g);
    }

    public void updateGroup(Group g, String name, String description) {
        g.setName(name);
        g.setDescription(description == null ? "" : description);
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
            // Atomic write: write to .tmp then rename
            File dir = ctx.getFilesDir();
            File tmp = new File(dir, "groups.json.tmp");
            File fin = new File(dir, "groups.json");
            try (FileWriter fw = new FileWriter(tmp)) { fw.write(arr.toString(2)); }
            tmp.renameTo(fin);
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
            int maxId = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject jo = arr.getJSONObject(i);
                int savedId = jo.optInt("id", -1);
                if (savedId <= 0) continue; // skip corrupt entries
                Group g = new Group(jo.optString("name", "Group"), jo.optString("description", ""));
                g.forceId(savedId);          // restore original ID
                g.setCreatedAt(jo.optLong("createdAt", System.currentTimeMillis()));
                groups.add(g);
                if (savedId > maxId) maxId = savedId;
            }
            Group.advanceCounter(maxId);     // ensure new groups get fresh IDs
        } catch (Exception ex) { ex.printStackTrace(); }
    }
}
