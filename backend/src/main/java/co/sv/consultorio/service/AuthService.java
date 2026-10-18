package co.sv.consultorio.service;

import co.sv.consultorio.domain.BusinessException;
import co.sv.consultorio.domain.ValidationException;
import co.sv.consultorio.repo.AdminRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.util.LinkedHashMap;
import java.util.Map;

/** Único usuario del programa: el Administrador. No se pueden crear más usuarios. */
public class AuthService {

    public static final String USERNAME = "Administrador";
    public static final int MIN_LENGTH = 6;

    private final AdminRepository repo;
    private final TimeSource time;

    public AuthService(AdminRepository repo, TimeSource time) {
        this.repo = repo;
        this.time = time;
    }

    /** false la primera vez que se abre el programa (aún no hay contraseña). */
    public boolean isConfigured() {
        return repo.passwordHash() != null;
    }

    public void setupPassword(String password, String repeat) {
        if (isConfigured()) {
            throw new BusinessException("La contraseña ya fue creada.");
        }
        Map<String, String> errors = new LinkedHashMap<>();
        validateNew(password, repeat, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        repo.savePasswordHash(USERNAME, hash(password), time.now());
    }

    public boolean login(String password) {
        String hash = repo.passwordHash();
        return hash != null && password != null && BCrypt.checkpw(password, hash);
    }

    public void changePassword(String current, String password, String repeat) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!login(current)) {
            errors.put("current", "La contraseña actual no es correcta.");
        }
        validateNew(password, repeat, errors);
        if (errors.isEmpty() && password.equals(current)) {
            errors.put("password", "La contraseña nueva debe ser distinta a la actual.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        repo.savePasswordHash(USERNAME, hash(password), time.now());
    }

    private static void validateNew(String password, String repeat, Map<String, String> errors) {
        if (password == null || password.length() < MIN_LENGTH) {
            errors.put("password", "Usa al menos " + MIN_LENGTH + " caracteres.");
        } else if (!password.equals(repeat)) {
            errors.put("repeat", "Las contraseñas no coinciden.");
        }
    }

    private static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(11));
    }
}
