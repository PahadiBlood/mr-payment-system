package xyz.rawmanoj.mrbank.exception;

import lombok.Getter;

@Getter
public class MrBankException extends RuntimeException {
    private final ErrorCode errorCode;

    public MrBankException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public MrBankException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
