package com.onlineshop.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.onlineshop.cart.CartItemNotFoundException;
import com.onlineshop.cart.QuantityLimitExceededException;
import com.onlineshop.catalog.ProductNotFoundException;
import com.onlineshop.inventory.InsufficientStockException;

import jakarta.validation.ConstraintViolationException;

/**
 * Returns errors as RFC 9457 problem details with a {@code code} field that
 * clients can rely on. Request validation errors are handled by the base class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        return buildProblem(HttpStatus.NOT_FOUND, ErrorCode.PRODUCT_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ProblemDetail handleCartItemNotFound(CartItemNotFoundException ex) {
        return buildProblem(HttpStatus.NOT_FOUND, ErrorCode.CART_ITEM_NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = buildProblem(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_STOCK, ex.getMessage());
        problem.setProperty("productId", ex.getProductId());
        return problem;
    }

    @ExceptionHandler(QuantityLimitExceededException.class)
    public ProblemDetail handleQuantityLimitExceeded(QuantityLimitExceededException ex) {
        return buildProblem(HttpStatus.BAD_REQUEST, ErrorCode.QUANTITY_LIMIT_EXCEEDED, ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ProblemDetail handleInvalidRequest(RuntimeException ex) {
        return buildProblem(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildProblem(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "The request conflicts with existing data, for example a duplicate SKU");
    }

    // Lock timeout or deadlock: the client can safely retry.
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleLockFailure(PessimisticLockingFailureException ex) {
        ProblemDetail problem = buildProblem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.TRY_AGAIN,
                "The cart is busy, please retry");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1")
                .body(problem);
    }

    private static ProblemDetail buildProblem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code.name());
        return problem;
    }

    enum ErrorCode {
        PRODUCT_NOT_FOUND,
        CART_ITEM_NOT_FOUND,
        INSUFFICIENT_STOCK,
        QUANTITY_LIMIT_EXCEEDED,
        INVALID_REQUEST,
        CONFLICT,
        TRY_AGAIN
    }
}
