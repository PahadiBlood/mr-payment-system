package xyz.rawmanoj.mrbank.exception;

public class UnauthorizedException extends MrBankException {
    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
