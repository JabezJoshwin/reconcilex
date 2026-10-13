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
    @ExceptionHandler({org.springframework.web.HttpRequestMethodNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotSupportedException.class,
        org.springframework.web.HttpMediaTypeNotAcceptableException.class,
        org.springframework.web.servlet.resource.NoResourceFoundException.class})
    ResponseEntity<?> frameworkError(Exception e) {
        var framework=(org.springframework.web.ErrorResponse)e;
        int status=framework.getStatusCode().value();
        var response=ResponseEntity.status(framework.getStatusCode()).headers(framework.getHeaders());
        // An unacceptable representation cannot carry our usual JSON error body.
        if(status==406) return response.build();
        String message=switch(status) {
            case 404 -> "Resource not found";
            case 405 -> "HTTP method not supported for this resource";
            case 415 -> "Request content type not supported; uploads require multipart/form-data";
            default -> "Invalid request";
        };
        return response.body(Map.of("code",status==404?"NOT_FOUND":"INVALID_REQUEST","message",message));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> unexpected(Exception e) { return error(500,"INTERNAL_ERROR","Request failed; check application and database availability"); }
    private ResponseEntity<Map<String,String>> error(int status,String code,String message) { return ResponseEntity.status(status).body(Map.of("code",code,"message",message)); }
}
