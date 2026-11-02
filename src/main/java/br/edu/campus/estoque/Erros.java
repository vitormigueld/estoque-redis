package br.edu.campus.estoque;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

public final class Erros {
    private Erros() {}

    public static class NaoEncontrado extends RuntimeException {
        public NaoEncontrado(String mensagem) { super(mensagem); }
    }

    public static class Conflito extends RuntimeException {
        public Conflito(String mensagem) { super(mensagem); }
    }

    public static class EstoqueInsuficiente extends RuntimeException {
        public EstoqueInsuficiente(String mensagem) { super(mensagem); }
    }

    @RestControllerAdvice
    public static class Handler {
        private static final Logger log = LoggerFactory.getLogger(Handler.class);

        @ExceptionHandler(NaoEncontrado.class)
        ResponseEntity<Map<String, Object>> naoEncontrado(NaoEncontrado e) {
            return resposta(HttpStatus.NOT_FOUND, e.getMessage());
        }

        @ExceptionHandler(Conflito.class)
        ResponseEntity<Map<String, Object>> conflito(Conflito e) {
            return resposta(HttpStatus.CONFLICT, e.getMessage());
        }

        @ExceptionHandler(EstoqueInsuficiente.class)
        ResponseEntity<Map<String, Object>> estoqueInsuficiente(EstoqueInsuficiente e) {
            return resposta(HttpStatus.CONFLICT, e.getMessage());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException e) {
            List<String> detalhes = new ArrayList<>(e.getBindingResult().getFieldErrors().stream()
                    .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                    .distinct()
                    .toList());
            detalhes.addAll(e.getBindingResult().getGlobalErrors().stream()
                    .map(erro -> erro.getObjectName() + ": " + erro.getDefaultMessage())
                    .toList());
            return resposta(HttpStatus.BAD_REQUEST, "Confira os campos enviados.", detalhes);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        ResponseEntity<Map<String, Object>> violacao(ConstraintViolationException e) {
            List<String> detalhes = e.getConstraintViolations().stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted()
                    .toList();
            return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido.", detalhes);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
            return resposta(HttpStatus.BAD_REQUEST, "JSON inválido ou campos em formato incorreto.");
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        ResponseEntity<Map<String, Object>> tipoInvalido(MethodArgumentTypeMismatchException e) {
            Class<?> tipo = e.getRequiredType();
            String esperado = tipo == null ? "válido" : tipo.getSimpleName();
            return resposta(HttpStatus.BAD_REQUEST,
                    "O parâmetro '" + e.getName() + "' deve ter um valor do tipo " + esperado + ".");
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        ResponseEntity<Map<String, Object>> parametroAusente(MissingServletRequestParameterException e) {
            return resposta(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + e.getParameterName() + ".");
        }

        @ExceptionHandler(IllegalArgumentException.class)
        ResponseEntity<Map<String, Object>> argumentoInvalido(IllegalArgumentException e) {
            return resposta(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        @ExceptionHandler(DataAccessException.class)
        ResponseEntity<Map<String, Object>> redisIndisponivel(DataAccessException e) {
            log.error("Falha ao acessar o Redis.", e);
            return resposta(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível acessar o Redis. Confira se o banco está ativo e tente novamente.");
        }

        @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
        ResponseEntity<Map<String, Object>> rotaNaoEncontrada(Exception e) {
            return resposta(HttpStatus.NOT_FOUND, "Rota não encontrada.");
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        ResponseEntity<Map<String, Object>> metodoNaoPermitido(HttpRequestMethodNotSupportedException e) {
            return resposta(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP não permitido para esta rota.");
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        ResponseEntity<Map<String, Object>> formatoNaoSuportado(HttpMediaTypeNotSupportedException e) {
            return resposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de conteúdo não suportado. Use JSON.");
        }

        @ExceptionHandler(Exception.class)
        ResponseEntity<Map<String, Object>> inesperado(Exception e) {
            log.error("Erro inesperado ao processar a requisição.", e);
            return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente mais tarde.");
        }

        private ResponseEntity<Map<String, Object>> resposta(HttpStatus status, String mensagem) {
            return resposta(status, mensagem, List.of());
        }

        private ResponseEntity<Map<String, Object>> resposta(HttpStatus status, String mensagem, List<String> detalhes) {
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("momento", Instant.now().toString());
            corpo.put("status", status.value());
            corpo.put("erro", mensagem == null || mensagem.isBlank() ? status.getReasonPhrase() : mensagem);
            if (!detalhes.isEmpty()) corpo.put("detalhes", detalhes);
            return ResponseEntity.status(status).body(corpo);
        }
    }
}
