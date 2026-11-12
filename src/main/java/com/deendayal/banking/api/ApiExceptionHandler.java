package com.deendayal.banking.api;

import com.deendayal.banking.AccountNotFoundException;
import com.deendayal.banking.DuplicateAccountException;
import com.deendayal.banking.InsufficientFundsException;
import com.deendayal.banking.InvalidAmountException;
import com.deendayal.banking.InvalidTransferException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e);
    }

    @ExceptionHandler(DuplicateAccountException.class)
    ProblemDetail duplicateAccount(DuplicateAccountException e) {
        return problem(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", e);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail insufficientFunds(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", e);
    }

    @ExceptionHandler(InvalidAmountException.class)
    ProblemDetail invalidAmount(InvalidAmountException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", e);
    }

    @ExceptionHandler(InvalidTransferException.class)
    ProblemDetail invalidTransfer(InvalidTransferException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers, HttpStatusCode statusCode,
                                                             WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            problem.setProperty("code", "INVALID_REQUEST");
        }
        return response;
    }

    private static ProblemDetail problem(HttpStatus status, String code, Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("code", code);
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", e);
    }

    @ExceptionHandler(IdempotencyRequestInProgressException.class)
    ProblemDetail idempotencyRequestInProgress(IdempotencyRequestInProgressException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS", e);
    }
}