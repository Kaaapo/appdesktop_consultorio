package co.sv.consultorio.repo;

import co.sv.consultorio.db.Database;

import java.util.HashMap;
import java.util.Map;

public class SettingsRepository {

    private final Database db;

    public SettingsRepository(Database db) {
        this.db = db;
    }

    public Map<String, String> all() {
        Map<String, String> out = new HashMap<>();
        db.query("SELECT key, value FROM setting", rs -> out.put(rs.getString(1), rs.getString(2)));
        return out;
    }

    public void put(String key, String value) {
        db.update("INSERT INTO setting (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                key, value);
    }
}
