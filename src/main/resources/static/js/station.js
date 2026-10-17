import {$,ensureRole,LiveConnection,roleLabel,statusLabel,formatTime,escapeHtml,instantButton,toast} from './common.js';
const role=await ensureRole(document.body.dataset.station);
const starter=role==='starter';$('#station-title').textContent=roleLabel(role);document.title=roleLabel(role)+' · Vereinsmeisterschaft';
const live=new LiveConnection(role);
live.onState=render;live.onChange=render;live.onPending=renderPending;live.onError=message=>toast(message,true);
let wakeLock=null;
async function wake() {
    if(!('wakeLock' in navigator) || !window.isSecureContext) {$('#wake-note').textContent='Bei dieser Verbindung bitte die automatische Bildschirmsperre in den Geräteeinstellungen ausschalten.';return;}
    try {wakeLock=await navigator.wakeLock.request('screen');$('#wake-note').textContent='Bildschirm wird wach gehalten, solange diese Seite sichtbar ist.';wakeLock.addEventListener('release',()=>{$('#wake-note').textContent='Bildschirmsperre wieder möglich. Bitte erneut aktivieren.';});}
    catch {$('#wake-note').textContent='Wachhalten nicht verfügbar. Bitte über die Geräteeinstellungen lösen.';}
}
$('#wake').addEventListener('click',wake);
$('#ready').addEventListener('click',()=>{try{const mine=live.state?.devices.find(d=>d.role===role);live.setReady(!mine?.ready);wake();}catch(e){toast(e.message,true);}});
instantButton($('#timing-button'),at=>{try{starter?live.startRace(at):live.stopRace(at);}catch(e){toast(e.message,true);}});
$('#false-start')?.addEventListener('click',()=>{try{live.falseStart();}catch(e){toast(e.message,true);}});
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&wakeLock) wake();});

function render() {
    const state=live.state,h=state?.activeHeat,lane=h?.lanes[0],myDevice=state?.devices.find(d=>d.role===role);
    const connected=live.connected();$('#connection').textContent=connected?'Verbunden':'Verbindung unterbrochen';$('#connection').className='badge '+(connected?'good':'bad');
    $('#clock-status').textContent=!connected&&live.hasCalibration()?'Zeitbasis für diesen Lauf gespeichert':live.clockReady()?`Zeitabgleich bereit · RTT ${live.best.rtt.toFixed(0)} ms`:'Zeitabgleich läuft …';
    $('#ready').disabled=!h || h.status!=='ARMED' || !connected || !live.clockReady() || (!starter&&!lane);
    $('#ready').textContent=myDevice?.ready?'Bereit ✓ · zurücknehmen':'Bereitschaft bestätigen';
    $('#ready').hidden=!!h && !['PLANNED','ARMED'].includes(h.status);
    $('#timing-button').disabled=true;
    $('#timing-button').textContent=starter?'START':'STOPP';
    if(starter) {
        const pending=live.pending.some(p=>p.event.type==='FALSE_START'&&p.event.heatId===h?.id);
        $('#false-start').hidden=!h || !['RUNNING','FINISHED'].includes(h.status);
        $('#false-start').disabled=!connected || pending;
        $('#false-start').textContent=pending?'Fehlstart wird übertragen …':'Fehlstart · Lauf wiederholen';
    }
    if(!h) {$('#heat-number').textContent='WARTE AUF LAUF';$('#swimmer').textContent='Noch kein Lauf aktiviert';$('#discipline').textContent='';$('#measurement-status').textContent='Die Verwaltung bereitet den Lauf vor.';if(starter) $('#lane-overview').innerHTML='';return;}
    $('#heat-number').textContent=`LAUF ${h.number} · ${statusLabel(h.status).toUpperCase()}`;
    $('#swimmer').textContent=starter?(h.label||h.discipline):lane?`${lane.firstName} ${lane.lastName}`:'Bahn nicht besetzt';
    $('#discipline').textContent=h.discipline+(lane&&!starter?` · Jahrgang ${lane.birthYear} · ${lane.category}`:'');
    const local=live.localEvent(starter?'START':'STOP',h.id);
    const queue=local?live.pending.find(p=>p.event.eventId===local.eventId):null;
    let message='';
    if(h.status==='ARMED') {
        const required=['starter',...h.lanes.map(l=>'lane'+l.lane)];
        const allReady=required.every(r=>state.devices.some(d=>d.role===r&&d.ready&&d.online));
        message=myDevice?.ready?(starter?(allReady?'Alle bereit. Beim Pfeifen START drücken.':'Warte auf die Bereitschaft der Bahnen.'):'Bereit. Warte auf den Starter.'):(h.falseStartOf?'Wiederholung nach Fehlstart. Alle Stationen bitte erneut Bereitschaft bestätigen.':'Name und Disziplin prüfen, dann Bereitschaft bestätigen.');
        if(starter) $('#timing-button').disabled=!(allReady && connected && live.clockReady() && live.hasCalibration() && !local);
        if(starter&&local) message=queue?.status==='rejected'?'START NICHT BESTÄTIGT! Lauf in der Verwaltung prüfen.':'Start wird übertragen …';
    } else if(h.status==='RUNNING') {
        if(starter) {message='Lauf gestartet. Bahnen nehmen die Zeiten.';$('#timing-button').textContent='GESTARTET';}
        else if(!lane) message='Diese Bahn ist in diesem Lauf frei.';
        else if(lane.status!=='PENDING') {message=`Am Laptop gespeichert: ${statusLabel(lane.status)}${lane.needsReview?' · Prüfung erforderlich':''}`;$('#timing-button').textContent='GESPEICHERT';}
        else if(local) {message=queue?.status==='rejected'?'Stopp abgelehnt. Lokale Sicherung unten prüfen.':queue?'Gestoppt · lokal gesichert. Übertragung zum Laptop läuft …':'Stopp am Laptop angekommen.';$('#timing-button').textContent='GESTOPPT';}
        else if(!live.hasCalibration()) message='Zeitabgleich dieses Laufes fehlt, z. B. nach Neuladen. Ersatzzeit nehmen und Verwaltung informieren.';
        else {message=connected?'Beim Anschlag STOPP drücken.':'Funkverbindung weg. STOPP bleibt möglich; die Zeit wird lokal gesichert.';$('#timing-button').disabled=false;}
        if(live.suspended) message+=' Seite wurde pausiert: Ergebnis muss geprüft werden.';
    } else if(h.status==='FINISHED') {
        message=starter?'Alle Ergebnisse liegen am Laptop vor.':lane?`Ergebnis gespeichert: ${statusLabel(lane.status)}${lane.needsReview?' · Bitte prüfen lassen.':''}`:'Lauf beendet.';$('#timing-button').textContent='BEENDET';
    } else if(['ABORTED','FALSE_START'].includes(h.status)) {message='Lauf abgebrochen. Warte auf die Wiederholung.';$('#timing-button').textContent='ABGEBROCHEN';}
    else message='Messung unterbrochen. Verwaltung informieren.';
    $('#measurement-status').textContent=message;
    if(starter) $('#lane-overview').innerHTML=h.lanes.map(l=>{const d=state.devices.find(x=>x.role==='lane'+l.lane);return `<div class="lane-row"><div><strong>Bahn ${l.lane}</strong><br><span class="small">${escapeHtml(l.firstName)} ${escapeHtml(l.lastName)}</span></div><span class="badge ${l.status!=='PENDING'?'good':d?.ready?'good':d?.online?'warn':'bad'}">${l.status!=='PENDING'?escapeHtml(l.status==='OK'?formatTime(l.elapsedMs):statusLabel(l.status)):d?.ready?'Bereit':d?.online?'Wartet':'Offline'}</span></div>`;}).join('');
}
function animate() {
    const h=live.state?.activeHeat;
    let ms=0;
    if(h) {
        const lane=h.lanes[0],stop=!starter?live.localEvent('STOP',h.id):null;
        if(!starter && lane?.elapsedMs!=null) ms=lane.elapsedMs;
        else if(stop && h.startMs!=null) ms=stop.atMs-h.startMs;
        else if(h.startMs!=null && h.status==='RUNNING') ms=live.estimatedNow()-h.startMs;
        else if(starter && h.status==='FINISHED') ms=Math.max(0,...h.lanes.map(l=>l.elapsedMs??0));
        else if(starter && live.localEvent('START',h.id) && h.status==='ARMED') ms=live.estimatedNow()-live.localEvent('START',h.id).atMs;
    }
    $('#timer').textContent=formatTime(ms);requestAnimationFrame(animate);
}
function renderPending() {
    $('#pending-panel').hidden=live.pending.length===0;
    $('#pending-list').innerHTML=live.pending.map(p=>`<div class="pending-item"><strong>${p.event.type==='START'?'Start':p.event.type==='FALSE_START'?'Fehlstart':'Stopp'} · Lauf ${escapeHtml(p.heatNumber??'?')} · ${p.status==='rejected'?'Prüfung erforderlich':'wartet auf Bestätigung'}</strong>${p.elapsedMs!=null?`<p>Lokale Zeit: <strong>${formatTime(p.elapsedMs)}</strong> (${p.elapsedMs} ms)</p>`:''}<p class="small">${escapeHtml(p.error||'Das Ereignis bleibt bis zur Bestätigung auf diesem Handy gespeichert.')}</p><details><summary class="small">Messdaten zur Wiederherstellung</summary><pre>${escapeHtml(JSON.stringify(p.event,null,2))}</pre></details><button class="quiet" data-discard="${escapeHtml(p.event.eventId)}">Nach Prüfung entfernen</button></div>`).join('');
}
$('#pending-list').addEventListener('click',e=>{const id=e.target.closest('[data-discard]')?.dataset.discard;if(id && confirm('Nur entfernen, wenn das Ergebnis am Laptop geprüft oder manuell übernommen wurde. Fortfahren?')) live.discard(id);});
live.start();animate();
if(!window.isSecureContext) $('#wake-note').textContent='Für lokale HTTP-Verbindungen: Bildschirmsperre bitte in den Geräteeinstellungen ausschalten.';
