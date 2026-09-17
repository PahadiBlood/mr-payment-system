package xyz.rawmanoj.mrbank.exception;

public class ForbiddenException extends MrBankException {
    public ForbiddenException() {
        super(ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
