package com.reconcilex.api;
import com.reconcilex.domain.*;
import com.reconcilex.service.ReconciliationService;
import org.apache.commons.csv.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ReconciliationController {
    private final ReconciliationService service;
    public ReconciliationController(ReconciliationService service) { this.service=service; }
    @GetMapping("/reconciliations")
    public RunHistory history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.history(page,size);
    }
    @PostMapping(value="/reconciliations",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RunResponse> create(@RequestParam MultipartFile internalFile, @RequestParam MultipartFile processorFile) throws IOException {
        validateSize(internalFile); validateSize(processorFile);
        try (var internal=internalFile.getInputStream(); var processor=processorFile.getInputStream()) {
            var r=service.create(internal,processor);
            return ResponseEntity.created(URI.create("/api/reconciliations/"+r.id())).body(r);
        }
    }
    private void validateSize(MultipartFile file) {
        if (file.getSize()>5L*1024*1024) throw new InputValidationException("Each file must be at most 5 MiB");
    }
    @GetMapping("/reconciliations/{id}")
    public RunDetails get(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size,@RequestParam(required=false) Classification classification) {
        return service.details(id,page,size,classification);
    }
    @GetMapping("/reconciliations/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable UUID id) throws IOException {
        var out=new StringWriter();
        try (var printer=new CSVPrinter(out,CSVFormat.RFC4180.builder().setHeader("transaction_id","classification","internal_amount_minor","internal_currency","processor_amount_minor","processor_currency","internal_occurrences","processor_occurrences").get())) {
            for (var f:service.export(id)) printer.printRecord(safeCell(f.transactionId()),f.classification(),f.internalAmountMinor(),safeCell(f.internalCurrency()),f.processorAmountMinor(),safeCell(f.processorCurrency()),f.internalOccurrences(),f.processorOccurrences());
        }
        return ResponseEntity.ok().contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"reconcilex-"+id+".csv\"")
            .header("X-Content-Type-Options","nosniff").body(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static String safeCell(String value) {
        if (value==null) return "";
        String stripped=value.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0) return "'"+value;
        return value;
    }
}
