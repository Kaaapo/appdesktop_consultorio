package co.sv.consultorio.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Errores de validación por campo (clave = nombre del campo, valor = mensaje). */
public class ValidationException extends BusinessException {

    private final Map<String, String> fieldErrors;

    public ValidationException(Map<String, String> fieldErrors) {
        super(fieldErrors.values().stream().findFirst().orElse("Revisa los datos."));
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
