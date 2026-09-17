package com.ecommerce.notification.exception;

import java.net.URI;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Tratamento centralizado de erros, no formato RFC 7807 (Problem Details). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://api.ecommerce.com/problems/";

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // A mensagem original fica no log: devolvê-la ao cliente vazaria detalhes internos.
        log.error("Erro não tratado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado ao processar a requisição");
        problem.setTitle("Erro interno");
        problem.setType(URI.create(BASE_TYPE + "internal-error"));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
