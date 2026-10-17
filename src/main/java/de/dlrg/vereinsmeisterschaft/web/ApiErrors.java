package de.dlrg.vereinsmeisterschaft.web;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> bad(IllegalArgumentException e) { return error(400,e.getMessage()); }
    @ExceptionHandler(IllegalStateException.class) public ResponseEntity<?> conflict(IllegalStateException e) { return error(409,e.getMessage()); }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> status(ResponseStatusException e) { return error(e.getStatusCode().value(),e.getReason()); }
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<?> json() { return error(400,"Ungültige Eingabe. Zahlen und Pflichtfelder prüfen."); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<?> large() { return error(413,"Datei zu groß. Excel-Dateien dürfen höchstens 5 MB, Sicherungen höchstens 20 MB groß sein."); }
    private ResponseEntity<?> error(int code,String message) { return ResponseEntity.status(code).body(Map.of("message",message==null?"Anfrage fehlgeschlagen.":message)); }
}
