package de.dlrg.vereinsmeisterschaft.web;

import com.fasterxml.jackson.databind.*;
import de.dlrg.vereinsmeisterschaft.model.Models.TimingEvent;
import de.dlrg.vereinsmeisterschaft.service.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component
public class RaceSocketHandler extends TextWebSocketHandler {
    private final RaceService races; private final ServerClock clock; private final ObjectMapper json;
    private final Map<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
    public RaceSocketHandler(RaceService races,ServerClock clock,ObjectMapper json) { this.races=races; this.clock=clock; this.json=json; }
    private String role(WebSocketSession s) { return String.valueOf(s.getAttributes().get(AccessService.ROLE)); }
    @Override public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        String role=role(raw);
        if(!List.of("admin","starter","lane1","lane2","lane3").contains(role)) { raw.close(CloseStatus.POLICY_VIOLATION.withReason("Bitte anmelden.")); return; }
        if(!races.connect(role,raw.getId())) { raw.close(CloseStatus.POLICY_VIOLATION.withReason("Rolle bereits verbunden. Alten Tab schließen.")); return; }
        var safe=new ConcurrentWebSocketSessionDecorator(raw,5000,512*1024);
        sessions.put(raw.getId(),safe); send(safe,races.state(role)); broadcast();
    }
    @Override protected void handleTextMessage(WebSocketSession raw,TextMessage message) {
        double received=clock.now();
        WebSocketSession session=sessions.get(raw.getId()); if(session==null) return;
        String eventId="";
        try {
            JsonNode n=json.readTree(message.getPayload());
            String type=n.path("type").asText(); String role=role(session); eventId=n.path("eventId").asText("");
            if(type.equals("SYNC")) {
                double c0=n.path("c0").asDouble(Double.NaN);
                if(!Double.isFinite(c0)) throw new IllegalArgumentException("Ungültige Synchronisierungsanfrage.");
                Double rtt=n.hasNonNull("rtt")?n.get("rtt").asDouble():null;
                races.heartbeat(role,session.getId(),rtt,n.path("timingValid").asBoolean(false));
                send(session,Map.of("type","SYNC","c0",c0,"s1",received,"s2",clock.now(),"bootId",clock.bootId()));
            } else if(type.equals("READY")) {
                races.ready(role,session.getId(),n.path("heatId").asText(),n.path("ready").asBoolean());
                broadcast();
            } else if(type.equals("START") || type.equals("STOP") || type.equals("FALSE_START")) {
                races.timing(role,session.getId(),json.treeToValue(n,TimingEvent.class));
                send(session,Map.of("type","ACK","eventId",eventId)); broadcast();
            } else throw new IllegalArgumentException("Unbekannte Nachricht.");
        } catch(IllegalArgumentException | IllegalStateException ex) {
            send(session,Map.of("type","ERROR","eventId",eventId,"message",ex.getMessage()));
        } catch(Exception ex) {
            LoggerFactory.getLogger(getClass()).error("WebSocket-Verarbeitung fehlgeschlagen",ex);
            send(session,Map.of("type","ERROR","eventId",eventId,"message","Ereignis konnte nicht gespeichert werden. Lokal behalten und in der Verwaltung prüfen."));
        }
    }
    @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus status) {
        sessions.remove(s.getId()); races.disconnect(role(s),s.getId()); broadcast();
    }
    @Override public void handleTransportError(WebSocketSession s,Throwable error) throws Exception { s.close(CloseStatus.SERVER_ERROR); }
    public void disconnectHttpSession(String id) {
        sessions.values().stream().filter(s->Objects.equals(s.getAttributes().get("HTTP.SESSION.ID"),id)).forEach(s->{
            try { s.close(CloseStatus.NORMAL); } catch(Exception ignored) {} });
    }
    public void broadcast() { sessions.values().forEach(s->send(s,races.state(role(s)))); }
    @Scheduled(fixedDelay=2000) public void tick() { broadcast(); }
    private void send(WebSocketSession s,Object data) {
        try { if(s.isOpen()) s.sendMessage(new TextMessage(json.writeValueAsString(data))); }
        catch(Exception ex) { try { s.close(CloseStatus.SERVER_ERROR); } catch(Exception ignored) {} }
    }
}
