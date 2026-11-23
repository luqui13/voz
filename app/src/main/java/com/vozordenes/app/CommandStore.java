package com.vozordenes.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lista de órdenes guardada en SharedPreferences como JSON. */
public final class CommandStore {
    private CommandStore() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("commands", Context.MODE_PRIVATE);
    }

    public static synchronized List<Command> load(Context c) {
        List<Command> out = new ArrayList<>();
        String raw = sp(c).getString("list", "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Command(o.getString("id"), o.getString("name"),
                        o.getString("type"), o.optString("param", "")));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static synchronized String loadRaw(Context c) {
        return sp(c).getString("list", "[]");
    }

    /** Valida y guarda una lista de órdenes en JSON. Lanza JSONException si no es válida. */
    public static synchronized int saveRaw(Context c, String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            o.getString("id");
            o.getString("name");
            o.getString("type");
        }
        sp(c).edit().putString("list", arr.toString()).apply();
        return arr.length();
    }

    public static synchronized void save(Context c, List<Command> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Command cmd : list) {
                JSONObject o = new JSONObject();
                o.put("id", cmd.id);
                o.put("name", cmd.name);
                o.put("type", cmd.type);
                o.put("param", cmd.param);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("list", arr.toString()).apply();
    }

    public static synchronized Command add(Context c, String name, String type, String param) {
        List<Command> list = load(c);
        Command cmd = new Command("c" + System.currentTimeMillis(), name, type, param);
        list.add(cmd);
        save(c, list);
        return cmd;
    }

    public static synchronized void remove(Context c, String id) {
        List<Command> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) { list.remove(i); break; }
        }
        save(c, list);
        TemplateStore.deleteAll(c, id);
    }

    public static Command find(Context c, String id) {
        for (Command cmd : load(c)) if (cmd.id.equals(id)) return cmd;
        return null;
    }
}
