package com.ecommerce.auth.exception;

/**
 * Credenciais inválidas.
 *
 * <p>A mensagem é a mesma para e-mail inexistente, senha errada e conta desativada:
 * distingui-las permitiria enumerar contas válidas.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos");
    }
}
