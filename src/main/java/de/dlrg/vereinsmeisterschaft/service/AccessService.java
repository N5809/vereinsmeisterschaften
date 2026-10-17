package de.dlrg.vereinsmeisterschaft.service;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessService {
    public static final String ROLE = "vmRole";
    private final Map<String,String> codes = new LinkedHashMap<>();
    public AccessService(@Value("${app.admin-password:}") String password) {
        SecureRandom random=new SecureRandom();
        if(password.isBlank()) {
            byte[] bytes=new byte[8]; random.nextBytes(bytes); password=HexFormat.of().formatHex(bytes);
        }
        codes.put("admin",password);
        for(String role:List.of("starter","lane1","lane2","lane3")) {
            String code;
            do { code=String.format("%06d",random.nextInt(1_000_000)); } while(codes.containsValue(code));
            codes.put(role,code);
        }
        LoggerFactory.getLogger(getClass()).info("VERWALTUNG: http://localhost:8080 | Admin-Passwort: {}",password);
    }
    public boolean valid(String role,String code) {
        String expected=codes.get(role);
        return expected!=null && code!=null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),code.getBytes(StandardCharsets.UTF_8));
    }
    public String role(HttpSession session) {
        Object role=session==null?null:session.getAttribute(ROLE);
        if(role==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Bitte anmelden.");
        return role.toString();
    }
    public void admin(HttpSession session) {
        if(!"admin".equals(role(session))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Nur die Verwaltung darf diese Aktion ausführen.");
    }
    public Map<String,String> stationCodes() {
        var copy=new LinkedHashMap<>(codes); copy.remove("admin"); return copy;
    }
}
