package com.ecommerce.auth.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        // A mensagem não repete o e-mail: a resposta de erro não deve servir para
        // descobrir quem tem conta no sistema.
        super("Não foi possível concluir o cadastro com os dados informados");
    }
}
