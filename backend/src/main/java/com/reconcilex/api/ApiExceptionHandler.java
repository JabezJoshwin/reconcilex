package com.reconcilex.api;
import com.reconcilex.domain.InputValidationException;
import com.reconcilex.service.RunNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InputValidationException.class)
    ResponseEntity<Map<String,String>> invalid(InputValidationException e) { return error(400,"INVALID_INPUT",e.getMessage()); }
    @ExceptionHandler({MaxUploadSizeExceededException.class})
    ResponseEntity<Map<String,String>> oversized(Exception e) { return error(400,"INVALID_INPUT","Each CSV must be at most 5 MiB; total multipart request at most 11 MiB"); }
    @ExceptionHandler({MissingServletRequestPartException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class,org.springframework.web.multipart.MultipartException.class})
    ResponseEntity<Map<String,String>> malformed(Exception e) { return error(400,"INVALID_REQUEST","Provide both CSV files and valid UUID, page, size and classification parameters"); }
    @ExceptionHandler(RunNotFoundException.class)
    ResponseEntity<Map<String,String>> missing() { return error(404,"NOT_FOUND","Reconciliation run not found"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> unexpected(Exception e) { return error(500,"INTERNAL_ERROR","Request failed; check application and database availability"); }
    private ResponseEntity<Map<String,String>> error(int status,String code,String message) { return ResponseEntity.status(status).body(Map.of("code",code,"message",message)); }
}
