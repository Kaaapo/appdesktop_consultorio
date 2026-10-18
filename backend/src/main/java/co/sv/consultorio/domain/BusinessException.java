package co.sv.consultorio.domain;

/** Error de regla de negocio con un mensaje listo para mostrar a la usuaria. */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
