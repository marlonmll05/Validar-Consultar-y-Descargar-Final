package com.certificadosapi.certificados.exception;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.fasterxml.jackson.core.JsonProcessingException;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja errores de tipo de argumento incorrecto (conversión de parámetros)
     * Retorna HTTP 400 - Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() == LocalDate.class) {
            return ResponseEntity
                .badRequest()
                .body("Formato de fecha inválido. Usa 'yyyy-MM-dd' sin espacios ni saltos de línea. Valor recibido: " + ex.getValue());
        }
        return ResponseEntity
            .badRequest()
            .body("Parámetro inválido: " + ex.getMessage());
    }

    /**
     * Maneja errores de validación (parámetros inválidos, reglas de negocio)
     * Retorna HTTP 400 - Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(
            IllegalArgumentException ex, 
            WebRequest request) {
        
        return ResponseEntity.badRequest().body("Error de parametros: " + ex.getMessage());
    }

    /**
     * Maneja errores de base de datos
     * Retorna HTTP 500 - Internal Server Error
     */
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<String> handleSQLException(
            SQLException ex, 
            WebRequest request) {
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error de base de datos: " + ex.getMessage());
    }

    /**
     * Maneja errores generales de runtime
     * Retorna HTTP 500 - Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(
            RuntimeException ex, 
            WebRequest request) {
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error en el servidor: " + ex.getMessage());
    }

    /**
     * Maneja cualquier otra excepción no capturada
     * Retorna HTTP 500 - Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGlobalException(
            Exception ex, 
            WebRequest request) {
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error inesperado: " + ex.getMessage());
    }

        @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error de entrada/salida: " + ex.getMessage());
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<String> handleJsonProcessingException(JsonProcessingException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al procesar JSON: " + ex.getMessage());
    }
}