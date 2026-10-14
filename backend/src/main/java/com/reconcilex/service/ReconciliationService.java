package com.reconcilex.service;
import com.reconcilex.api.*;
import com.reconcilex.core.*;
import com.reconcilex.domain.*;
import com.reconcilex.persistence.RunStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
@Service
public class ReconciliationService {
    private final RunStore store;
    public ReconciliationService(RunStore store) { this.store = store; }
    @Transactional
    public RunResponse create(InputStream internalInput, InputStream processorInput) {
        var parser = new CsvTransactionParser();
        var internal = parser.parse(internalInput,"internalFile");
        var processor = parser.parse(processorInput,"processorFile");
        var findings = new ReconciliationEngine().reconcile(internal,processor);
        var counts = new EnumMap<Classification,Long>(Classification.class);
        for (var c : Classification.values()) counts.put(c,0L);
        for (var f : findings) counts.merge(f.classification(),1L,Long::sum);
        var run = new RunResponse(UUID.randomUUID(),Instant.now(),"COMPLETED", new RunSummary(internal.size(),processor.size(),findings.size(),counts));
        store.save(com.reconcilex.auth.CurrentUser.id(),run,findings,internal,processor);
        return run;
    }
    public RunResponse require(UUID id) { return store.find(com.reconcilex.auth.CurrentUser.id(),id).orElseThrow(() -> new RunNotFoundException()); }
    @Transactional(readOnly=true)
    public RunDetails details(UUID id, int page, int size, Classification filter) {
        if (page < 0 || size < 1 || size > 500 || (long)page*size > Integer.MAX_VALUE) throw new InputValidationException("page must be nonnegative and size must be 1..500");
        var r = require(id);
        var owner=com.reconcilex.auth.CurrentUser.id();
        return new RunDetails(r.id(),r.createdAt(),r.status(),r.summary(),store.findings(owner,id,page*size,size,filter),page,size,store.count(owner,id,filter));
    }
    @Transactional(readOnly=true)
    public com.reconcilex.api.RunHistory history(int page,int size) {
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) throw new InputValidationException("page must be nonnegative and size must be 1..100");
        var owner=com.reconcilex.auth.CurrentUser.id();
        return new com.reconcilex.api.RunHistory(store.history(owner,page*size,size),page,size,store.historyCount(owner));
    }
    @Transactional(readOnly=true)
    public List<Finding> export(UUID id) { require(id); return store.findings(com.reconcilex.auth.CurrentUser.id(),id,0,40000,null); }
}
