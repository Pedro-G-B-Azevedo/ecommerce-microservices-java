package com.ecommerce.inventory.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gera ou reaproveita o id de correlação de cada requisição, o expõe no MDC —
 * daí ele entra em toda linha de log JSON desta requisição — e o devolve no
 * cabeçalho de resposta, para que quem chamou também possa rastreá-la.
 *
 * <p>Roda antes de tudo, inclusive da cadeia de segurança: mesmo uma resposta
 * 401 sai com o id de correlação, e a tentativa fica com um rastro nos logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationId.currentOrGenerate(request.getHeader(CorrelationId.HEADER));
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Threads de servlet são reaproveitadas entre requisições: sem isso,
            // o id de uma requisição vazaria para os logs da próxima que caísse
            // na mesma thread.
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
