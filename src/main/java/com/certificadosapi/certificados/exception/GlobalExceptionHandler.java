package com.certificadosapi.certificados.exception;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.fasterxml.jackson.core.JsonProcessingException;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Logger agregado aquí
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maneja errores de tipo de argumento incorrecto (conversión de parámetros)
     * Retorna HTTP 400 - Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() == LocalDate.class) {
            log.warn("Formato de fecha inválido. Valor recibido: {}", ex.getValue());
            return ResponseEntity
                .badRequest()
                .body("Formato de fecha inválido. Usa 'yyyy-MM-dd' sin espacios ni saltos de línea. Valor recibido: " + ex.getValue());
        }
        log.warn("Tipo de parámetro incorrecto: {} - Esperado: {}, Recibido: {}", 
                 ex.getName(), ex.getRequiredType(), ex.getValue());
        return ResponseEntity
            .badRequest()
            .body("Parámetro inválido: " + ex.getMessage());
    }
    
    /**
     * Maneja errores de no contenido
     * Retorna HTTP 204 - NO CONTENT
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleNoSuchElement(NoSuchElementException ex) {
        log.warn("Elemento no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * Maneja errores de validación (parámetros inválidos, reglas de negocio)
     * Retorna HTTP 400 - Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(
            IllegalArgumentException ex, 
            WebRequest request) {
        
        log.warn("Argumento inválido: {} - URI: {}", ex.getMessage(), request.getDescription(false));
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
        
        log.error("Error de base de datos - SQL State: {}, Error Code: {}, URI: {}", 
                  ex.getSQLState(), ex.getErrorCode(), request.getDescription(false));
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error de base de datos: " + ex.getMessage());
    }

    /**
     * Maneja errores de entrada/salida (archivos, streams)
     * Retorna HTTP 500 - Internal Server Error
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
        log.error("Error de entrada/salida: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error de entrada/salida: " + ex.getMessage());
    }

    /**
     * Maneja errores al procesar JSON
     * Retorna HTTP 500 - Internal Server Error
     */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<String> handleJsonProcessingException(JsonProcessingException ex) {
        log.error("Error al procesar JSON: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error al procesar JSON: " + ex.getMessage());
    }

    /**
     * Maneja errores generales de runtime
     * Retorna HTTP 500 - Internal Server Error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(
            RuntimeException ex, 
            WebRequest request) {
        
        log.error("Error de runtime en URI: {} - Mensaje: {}", 
                  request.getDescription(false), ex.getMessage());
        
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
        
        log.error("Error inesperado no manejado - Tipo: {}, URI: {}, Mensaje: {}", 
                  ex.getClass().getName(), request.getDescription(false), ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error inesperado: " + ex.getMessage());
    }
}