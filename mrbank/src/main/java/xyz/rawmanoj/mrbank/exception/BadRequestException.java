package xyz.rawmanoj.mrbank.exception;

public class BadRequestException extends MrBankException {
    public BadRequestException() {
        super(ErrorCode.BAD_REQUEST);
    }

    public BadRequestException(String message) {
        super(ErrorCode.BAD_REQUEST, message);
    }
}
