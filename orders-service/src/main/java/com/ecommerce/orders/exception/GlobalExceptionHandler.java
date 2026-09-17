package com.ecommerce.orders.exception;

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

/**
 * Tratamento centralizado de erros, no formato RFC 7807 (Problem Details).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://api.ecommerce.com/problems/";

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Pedido não encontrado", ex.getMessage(), "order-not-found");
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidState(InvalidOrderStateException ex) {
        return problem(HttpStatus.CONFLICT, "Transição de status inválida", ex.getMessage(), "invalid-order-state");
    }

    @ExceptionHandler(DuplicateOrderItemException.class)
    public ProblemDetail handleDuplicateItem(DuplicateOrderItemException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Item duplicado", ex.getMessage(), "duplicate-order-item");
    }

    @ExceptionHandler(ProductUnavailableException.class)
    public ProblemDetail handleProductUnavailable(ProductUnavailableException ex) {
        // 422 e não 400: o corpo está bem formado, mas referencia produtos que o
        // catálogo não reconhece.
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Produto indisponível",
                "Um ou mais produtos do pedido não existem ou estão inativos", "product-unavailable");
        problem.setProperty("productIds", ex.getProductIds());
        return problem;
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    public ProblemDetail handleInventoryUnavailable(InventoryUnavailableException ex) {
        log.warn("Catálogo indisponível ao criar pedido", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Serviço indisponível",
                "Não foi possível consultar o catálogo de produtos; tente novamente em instantes",
                "inventory-unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Requisição inválida",
                "Um ou mais campos não passaram na validação",
                "validation-error");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // A mensagem original fica no log: devolvê-la ao cliente vazaria detalhes internos.
        log.error("Erro não tratado", ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno",
                "Ocorreu um erro inesperado ao processar a requisição",
                "internal-error");
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
