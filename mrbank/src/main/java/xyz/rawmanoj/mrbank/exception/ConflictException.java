package xyz.rawmanoj.mrbank.exception;

public class ConflictException extends MrBankException {
    public ConflictException() {
        super(ErrorCode.CONFLICT);
    }

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
