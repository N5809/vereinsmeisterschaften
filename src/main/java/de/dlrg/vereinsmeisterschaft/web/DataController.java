package de.dlrg.vereinsmeisterschaft.web;

import de.dlrg.vereinsmeisterschaft.model.BackupModels.*;
import de.dlrg.vereinsmeisterschaft.service.*;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/data")
public class DataController {
    private final BackupService backups;private final AccessService access;private final RaceSocketHandler socket;
    public DataController(BackupService backups,AccessService access,RaceSocketHandler socket) {this.backups=backups;this.access=access;this.socket=socket;}
    @GetMapping public DataSummary summary(HttpSession s) {access.admin(s);return backups.summary();}
    @PostMapping("/export") public ResponseEntity<byte[]> export(@RequestBody Selection selection,HttpSession s) {
        access.admin(s);byte[] bytes=backups.write(selection);
        String name="vereinsmeisterschaft-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+".json";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+name+"\"")
                .contentType(MediaType.APPLICATION_JSON).body(bytes);
    }
    @PostMapping("/preview") public BackupPreview preview(@RequestParam("file") MultipartFile file,HttpSession s) throws IOException {
        access.admin(s);return backups.preview(file.getBytes());
    }
    @PostMapping("/restore") public Map<String,Boolean> restore(@RequestParam("file") MultipartFile file,@RequestParam String confirmation,HttpSession s) throws IOException {
        access.admin(s);backups.restore(file.getBytes(),confirmation);socket.broadcast();return Map.of("ok",true);
    }
    @PostMapping("/reset") public Map<String,Boolean> reset(@RequestBody Confirmation confirmation,HttpSession s) {
        access.admin(s);backups.reset(confirmation.confirmation());socket.broadcast();return Map.of("ok",true);
    }
}
