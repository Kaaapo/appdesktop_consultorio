package co.sv.consultorio.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Conexión única a SQLite (programa de un solo usuario en un solo PC).
 * Aplica las migraciones pendientes al abrir.
 */
public final class Database implements AutoCloseable {

    private final Connection connection;
    private final Path file;

    private Database(Connection connection, Path file) {
        this.connection = connection;
        this.file = file;
    }

    public static Database openFile(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            Database db = new Database(c, file);
            db.init();
            return db;
        } catch (SQLException | IOException e) {
            throw new DataAccessException("No se pudo abrir la base de datos: " + file, e);
        }
    }

    public static Database openInMemory() {
        try {
            Database db = new Database(DriverManager.getConnection("jdbc:sqlite::memory:"), null);
            db.init();
            return db;
        } catch (SQLException e) {
            throw new DataAccessException("No se pudo abrir la base de datos en memoria", e);
        }
    }

    private void init() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        }
        Migrations.apply(this);
    }

    public Connection connection() {
        return connection;
    }

    public int userVersion() {
        return queryOne("PRAGMA user_version", rs -> rs.getInt(1));
    }

    void setUserVersion(int v) {
        execute("PRAGMA user_version = " + v);
    }

    public void execute(String sql) {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    public int update(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    /** Ejecuta un INSERT y devuelve el id generado. */
    public long insert(String sql, Object... params) {
        try (PreparedStatement ps = prepare(sql, params)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = prepare(sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> out = new ArrayList<>();
            while (rs.next()) {
                out.add(mapper.map(rs));
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException(sql, e);
        }
    }

    public <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public <T> T inTransaction(Supplier<T> work) {
        try {
            boolean auto = connection.getAutoCommit();
            if (!auto) {
                return work.get();
            }
            connection.setAutoCommit(false);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error en la transacción", e);
        }
    }

    public void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    private PreparedStatement prepare(String sql, Object... params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof Boolean b) {
                ps.setInt(i + 1, b ? 1 : 0);
            } else if (p instanceof Enum<?> en) {
                ps.setString(i + 1, en.name());
            } else if (p instanceof java.time.temporal.Temporal t) {
                ps.setString(i + 1, DbTypes.format(t));
            } else {
                ps.setObject(i + 1, p);
            }
        }
        return ps;
    }

    /**
     * Copia de seguridad diaria en {@code backups/} junto a la base de datos.
     * Conserva las {@code keep} copias más recientes.
     */
    public void dailyBackup(LocalDate today, int keep) {
        if (file == null) {
            return;
        }
        Path dir = file.toAbsolutePath().getParent().resolve("backups");
        Path target = dir.resolve(baseName() + "-" + today + ".db");
        Path partial = dir.resolve(target.getFileName() + ".tmp");
        Pattern copyName = Pattern.compile(Pattern.quote(baseName()) + "-\\d{4}-\\d{2}-\\d{2}\\.db");
        try {
            Files.createDirectories(dir);
            if (!Files.exists(target)) {
                // Una copia cortada (disco lleno, apagón) no debe quedar con el nombre de una copia válida.
                Files.deleteIfExists(partial);
                execute("VACUUM INTO '" + partial.toString().replace("'", "''") + "'");
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            }
            List<Path> copies;
            try (Stream<Path> s = Files.list(dir)) {
                copies = s.filter(p -> copyName.matcher(p.getFileName().toString()).matches())
                        .sorted(Comparator.comparing(Path::getFileName).reversed())
                        .toList();
            }
            for (int i = keep; i < copies.size(); i++) {
                Files.deleteIfExists(copies.get(i));
            }
        } catch (IOException | RuntimeException e) {
            partial.toFile().delete();
            System.err.println("No se pudo crear la copia de seguridad: " + e.getMessage());
        }
    }

    /** Copia consistente para llevar a otra memoria. No reemplaza las copias diarias. */
    public void copyTo(Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.deleteIfExists(target);
        execute("VACUUM INTO '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
    }

    private String baseName() {
        String n = file.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // cerrando el programa
        }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
