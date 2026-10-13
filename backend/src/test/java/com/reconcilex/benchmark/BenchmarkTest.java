package com.reconcilex.benchmark;
import com.reconcilex.core.*;
import com.reconcilex.domain.Classification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
/** Opt-in local diagnostic, not a performance SLA or a substitute for JMH. */
@EnabledIfSystemProperty(named="reconcilex.benchmark", matches="true")
class BenchmarkTest {
    @Test void tenThousandRowsPerSource() {
        var internal=new StringBuilder("transaction_id,amount_minor,currency\n");
        var processor=new StringBuilder("transaction_id,amount_minor,currency\n");
        for (int i=0;i<10_000;i++) internal.append("bench_").append(i).append(',').append((long)i+1).append(",INR\n");
        for (int i=9_999;i>=0;i--) processor.append("bench_").append(i).append(',').append((long)i+1+(i%10==0?1:0)).append(",INR\n");
        byte[] left=internal.toString().getBytes(StandardCharsets.UTF_8),right=processor.toString().getBytes(StandardCharsets.UTF_8);
        for(int i=0;i<3;i++) measure(left,right);
        long[] nanos=new long[5];
        for(int i=0;i<nanos.length;i++) nanos[i]=measure(left,right);
        System.out.printf("BENCHMARK rowsPerFile=10000 warmups=3 measured=5 java=%s os=%s arch=%s processors=%d maxHeapMiB=%d%n",
            System.getProperty("java.version"),System.getProperty("os.name"),System.getProperty("os.arch"),Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().maxMemory()/(1024*1024));
        System.out.println("BENCHMARK parse+match nanos="+Arrays.toString(nanos));
        Arrays.sort(nanos);
        System.out.printf(java.util.Locale.ROOT,"BENCHMARK medianMs=%.3f matches=9000 amountMismatches=1000 excludes=database,http,export%n",nanos[2]/1_000_000.0);
    }
    private long measure(byte[] left,byte[] right) {
        long start=System.nanoTime();
        var parser=new CsvTransactionParser();
        var results=new ReconciliationEngine().reconcile(parser.parse(new ByteArrayInputStream(left),"internal"),parser.parse(new ByteArrayInputStream(right),"processor"));
        long elapsed=System.nanoTime()-start;
        assertThat(results).hasSize(10_000);
        assertThat(results.stream().filter(f->f.classification()==Classification.MATCHED).count()).isEqualTo(9_000);
        assertThat(results.stream().filter(f->f.classification()==Classification.AMOUNT_MISMATCH).count()).isEqualTo(1_000);
        return elapsed;
    }
}
