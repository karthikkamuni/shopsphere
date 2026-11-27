package com.shopsphere.order;
import org.springframework.http.ResponseEntity;import org.springframework.http.converter.HttpMessageNotReadableException;import org.springframework.security.access.AccessDeniedException;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.ExceptionHandler;import org.springframework.web.bind.annotation.RestControllerAdvice;import org.springframework.web.server.ResponseStatusException;import java.util.Map;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("error",e.getReason()==null?"Request failed":e.getReason()));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("error","Invalid request","details",e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+": "+x.getDefaultMessage()).toList()));}
 @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> unreadable(HttpMessageNotReadableException e){return ResponseEntity.badRequest().body(Map.of("error","Malformed or missing request body"));}
 @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(AccessDeniedException e){return ResponseEntity.status(403).body(Map.of("error",e.getMessage()!=null?e.getMessage():"Access denied"));}
}
