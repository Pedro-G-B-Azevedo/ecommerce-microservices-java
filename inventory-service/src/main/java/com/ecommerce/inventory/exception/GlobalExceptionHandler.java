package com.ecommerce.inventory.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Tratamento centralizado de erros, no formato RFC 7807 (Problem Details). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://api.ecommerce.com/problems/";

    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Produto não encontrado", ex.getMessage(), "product-not-found");
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handleReservationNotFound(ReservationNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Reserva não encontrada", ex.getMessage(), "reservation-not-found");
    }

    @ExceptionHandler(DuplicateSkuException.class)
    public ProblemDetail handleDuplicateSku(DuplicateSkuException ex) {
        return problem(HttpStatus.CONFLICT, "SKU já cadastrado", ex.getMessage(), "duplicate-sku");
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    public ProblemDetail handleInvalidReservationState(InvalidReservationStateException ex) {
        return problem(HttpStatus.CONFLICT, "Transição de reserva inválida", ex.getMessage(),
                "invalid-reservation-state");
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Estoque insuficiente", ex.getMessage(),
                "insufficient-stock");
        problem.setProperty("shortfalls", ex.getShortfalls());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "Um ou mais campos não passaram na validação", "validation-error");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // A mensagem original fica no log: devolvê-la ao cliente vazaria detalhes internos.
        log.error("Erro não tratado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado ao processar a requisição", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE_TYPE + type));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    public record ValidationError(String field, String message) {
    }
}
