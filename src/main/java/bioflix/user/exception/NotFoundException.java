package bioflix.user.exception;

/** Mapped to HTTP 404 by GlobalExceptionHandler. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
