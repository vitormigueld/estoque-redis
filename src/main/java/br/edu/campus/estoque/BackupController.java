package br.edu.campus.estoque;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

/** Dump/restore usando os comandos nativos DUMP e RESTORE (serialização binária do Redis). */
@RestController
@RequestMapping("/admin")
public class BackupController {
    private static final Path DIR = Paths.get("backups");
    private final StringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();

    public BackupController(StringRedisTemplate redis) { this.redis = redis; }

    private Path arquivo(String nome) throws IOException {
        Files.createDirectories(DIR);
        return DIR.resolve(Paths.get(nome).getFileName());   // evita path traversal
    }

    @PostMapping("/dump")
    public Map<String, Object> dump(@RequestParam(defaultValue = "estoque.jsonl") String arquivo) throws IOException {
        int total = 0;
        try (var w = Files.newBufferedWriter(arquivo(arquivo));
             Cursor<String> c = redis.scan(ScanOptions.scanOptions().match("*").count(200).build())) {  // SCAN, nunca KEYS
            while (c.hasNext()) {
                String k = c.next();
                byte[] d = redis.dump(k);
                if (d == null) continue;
                Long ttl = redis.getExpire(k, TimeUnit.MILLISECONDS);
                w.write(om.writeValueAsString(Map.of("key", k, "ttl", ttl == null || ttl < 0 ? 0 : ttl,
                        "data", Base64.getEncoder().encodeToString(d))));
                w.newLine();
                total++;
            }
        }
        return Map.of("arquivo", "backups/" + arquivo, "chaves", total);
    }

    @PostMapping("/restore")
    public Map<String, Object> restore(@RequestParam String arquivo) throws IOException {
        int total = 0;
        for (String linha : Files.readAllLines(arquivo(arquivo))) {
            if (linha.isBlank()) continue;
            var n = om.readTree(linha);
            redis.restore(n.get("key").asText(), Base64.getDecoder().decode(n.get("data").asText()),
                    n.get("ttl").asLong(), TimeUnit.MILLISECONDS, true);   // true = REPLACE
            total++;
        }
        return Map.of("chaves", total);
    }
}
