export const $=selector=>document.querySelector(selector);
export const escapeHtml=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const roleLabel=role=>({admin:'Verwaltung',starter:'Starter',lane1:'Bahn 1',lane2:'Bahn 2',lane3:'Bahn 3'}[role]||role);
export const statusLabel=status=>({PLANNED:'Geplant',ARMED:'Startvorbereitung',RUNNING:'Läuft',FINISHED:'Beendet',ABORTED:'Abgebrochen',FALSE_START:'Fehlstart',INTERRUPTED:'Unterbrochen',PENDING:'Offen',OK:'Zeit vorhanden',DNS:'Nicht gestartet',DNF:'Nicht beendet',DSQ:'Disqualifiziert',MISSING:'Zeit fehlt',REVIEW:'Prüfung erforderlich'}[status]||status);
export const destination=role=>role==='admin'?'/manager.html':role==='starter'?'/starter.html':'/bahn.html';
export async function api(path,options={}) {
    const init={...options,headers:{...options.headers}};
    if(options.body && !(options.body instanceof FormData)) { init.body=JSON.stringify(options.body); init.headers['Content-Type']='application/json'; }
    const response=await fetch('/api'+path,init);
    const data=await response.json().catch(()=>({message:'Der Server hat keine gültige Antwort geliefert.'}));
    if(!response.ok) { if(response.status===401 && path!=='/login' && location.pathname!=='/' && location.pathname!=='/index.html') location.href='/'; throw new Error(data.message||'Anfrage fehlgeschlagen.'); }
    return data;
}
export async function ensureRole(expected) {
    const me=await api('/me');
    if(expected==='lane'?!/^lane[1-3]$/.test(me.role):me.role!==expected) { location.href=destination(me.role); throw new Error('Falsche Ansicht.'); }
    $('#logout')?.addEventListener('click',async()=>{ if(!confirm('Station abmelden? Während eines Laufes sollte die Seite geöffnet bleiben.')) return; await api('/logout',{method:'POST'}); location.href='/'; });
    return me.role;
}
export function formatTime(ms) {
    if(ms==null || !Number.isFinite(ms)) return '—';
    const cs=Math.floor(Math.max(0,ms)/10), minutes=Math.floor(cs/6000),seconds=Math.floor(cs/100)%60;
    return `${String(minutes).padStart(2,'0')}:${String(seconds).padStart(2,'0')},${String(cs%100).padStart(2,'0')}`;
}
export function parseTime(text) {
    const value=text.trim().replace(',','.');
    if(!/^\d+(?::[0-5]\d)?(?:\.\d{1,3})?$/.test(value)) throw new Error('Zeit als Sekunden oder mm:ss,hh eingeben.');
    const parts=value.split(':'); return Math.round((parts.length===2?Number(parts[0])*60+Number(parts[1]):Number(parts[0]))*1000);
}
export function formatTimeMs(ms) {return ms==null||!Number.isFinite(ms)?'—':formatTime(ms)+Math.floor(Math.max(0,ms))%10;}
export function toast(message,error=false) { const el=$('#toast'); if(el) { el.textContent=message; el.classList.toggle('error',error); el.hidden=false; } }
function uuid() {
    // crypto.randomUUID ist auf HTTP-LAN-Adressen nicht immer verfuegbar.
    const bytes=new Uint8Array(16); crypto.getRandomValues(bytes); bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
    const s=[...bytes].map(x=>x.toString(16).padStart(2,'0')).join(''); return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`;
}
export function instantButton(element,fn) {
    element.addEventListener('pointerdown',e=>{ if(element.disabled || !e.isPrimary || e.button!==0) return; const at=performance.now(); e.preventDefault(); fn(at); });
    element.addEventListener('keydown',e=>{ if(element.disabled || e.repeat || !['Enter',' '].includes(e.key)) return; const at=performance.now(); e.preventDefault(); fn(at); });
}

/** Zeitabgleich auf Anwendungsebene. RTT/2 ist nur ein Indikator, keine Fehlergarantie. */
export class LiveConnection {
    constructor(role) {
        this.role=role;this.state=null;this.ws=null;this.samples=[];this.best=null;this.lastSync=0;this.lastStatePerf=0;this.frozen=null;this.suspended=false;this.stopped=new Map();this.started=new Map();this.halted=false;
        this.pendingKey='vm.pending.'+role;this.dataKey='vm.dataId.'+role;this.dataId=null;this.voidedEvents=new Set();
        try{this.dataId=localStorage.getItem(this.dataKey);}catch{}
        try { this.pending=JSON.parse(localStorage.getItem(this.pendingKey)||'[]'); if(!Array.isArray(this.pending)) this.pending=[]; }
        catch { this.pending=[]; }
        this.onState=()=>{};this.onChange=()=>{};this.onError=message=>toast(message,true);this.onPending=()=>{};
        this.lastPerf=performance.now();this.lastWall=Date.now();
        document.addEventListener('visibilitychange',()=>{
            if(document.hidden) this.flagSuspension();
            else { this.samples=[];this.best=null;this.sync(); }
        });
        window.addEventListener('pagehide',()=>this.flagSuspension());
    }
    start() {
        this.connect(); this.onPending();
        this.timer=setInterval(()=>{
            const p=performance.now(),w=Date.now();
            if(Math.abs((w-this.lastWall)-(p-this.lastPerf))>150 || p-this.lastPerf>5000) this.flagSuspension();
            this.lastPerf=p;this.lastWall=w;
            if(this.state?.activeHeat?.status==='ARMED' && this.frozen && p-this.frozen.created>120000) { this.setReady(false);this.onError('Bereitschaft abgelaufen. Bitte erneut bestätigen.'); }
            this.sync();this.flush();this.onChange();
        },1000);
    }
    connect() {
        if(this.halted) return;
        const ws=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`); this.ws=ws;
        ws.onopen=()=>{this.samples=[];this.best=null;this.lastSync=0;this.sync();this.flush();this.onChange();};
        ws.onmessage=e=>{try{this.message(JSON.parse(e.data));}catch(error){this.onError('Antwort konnte nicht verarbeitet werden: '+error.message);}};
        ws.onclose=e=>{
            this.lastSync=0;
            if(e.code===1008) {this.halted=true;this.onError(e.reason||'Rolle bereits geöffnet. Alten Tab schließen und diese Seite neu laden.');}
            else setTimeout(()=>this.connect(),1200);
            this.onChange();
        };
        ws.onerror=()=>{};
    }
    connected() { return this.ws?.readyState===WebSocket.OPEN && this.lastSync>0 && performance.now()-this.lastSync<5000; }
    send(message) { if(this.ws?.readyState===WebSocket.OPEN) this.ws.send(JSON.stringify(message)); }
    sync() {
        if(this.ws?.readyState!==WebSocket.OPEN) return;
        const running=this.state?.activeHeat?.status==='RUNNING';
        this.send({type:'SYNC',c0:performance.now(),rtt:this.best?.rtt??null,timingValid:running?!!this.frozen&&!this.suspended:this.clockReady()});
    }
    clockReady() { return !document.hidden && this.lastSync>0 && this.samples.length>=3 && this.best!=null && this.best.rtt<=(this.state?.maxRoundtripMs??100) && performance.now()-this.lastSync<5000; }
    message(n) {
        const now=performance.now();
        if(n.type==='SYNC') {
            if(this.state && n.bootId!==this.state.bootId) {this.flagSuspension();this.samples=[];this.best=null;return;}
            const rtt=(now-n.c0)-(n.s2-n.s1), offset=((n.s1-n.c0)+(n.s2-now))/2;
            if(Number.isFinite(rtt) && Number.isFinite(offset) && rtt>=-1 && rtt<1500) {
                this.lastSync=now;this.samples=this.samples.filter(s=>now-s.at<15000);
                this.samples.push({rtt:Math.max(0,rtt),offset,at:now});this.samples=this.samples.slice(-20);
                this.best=this.samples.reduce((a,b)=>a.rtt<=b.rtt?a:b);
                if(this.samples.length<8) setTimeout(()=>this.sync(),60);
            }
            this.onChange();
        } else if(n.type==='STATE') {
            const previous=this.state;
            const replaced=!!this.dataId && !!n.dataId && this.dataId!==n.dataId;
            if(replaced) {
                this.pending.forEach(p=>this.voidedEvents.add(p.event.eventId));
                this.pending=[];this.started.clear();this.stopped.clear();this.frozen=null;this.samples=[];this.best=null;this.lastSync=0;this.suspended=false;
                this.persist();this.onPending();
            }
            if(n.dataId){this.dataId=n.dataId;try{localStorage.setItem(this.dataKey,n.dataId);}catch{}}
            if(previous && previous.bootId!==n.bootId && !replaced) {this.frozen=null;this.samples=[];this.best=null;this.suspended=true;this.onError('Serverneustart erkannt. Vorhandene Messung in der Verwaltung prüfen.');}
            if(previous?.activeHeat?.id!==n.activeHeat?.id) {this.frozen=null;this.suspended=false;}
            const falseStartOf=n.activeHeat?.falseStartOf;
            if(falseStartOf) {
                const obsolete=this.pending.filter(p=>p.event.heatId===falseStartOf);
                if(obsolete.length) {
                    obsolete.forEach(p=>this.voidedEvents.add(p.event.eventId));
                    this.pending=this.pending.filter(p=>p.event.heatId!==falseStartOf);this.persist();this.onPending();
                }
                this.started.delete(falseStartOf);this.stopped.delete(falseStartOf);
            }
            this.state=n;this.lastStatePerf=now;this.onState(n);this.onChange();
        } else if(n.type==='ACK') {
            this.pending=this.pending.filter(p=>p.event.eventId!==n.eventId);this.persist();this.onPending();this.onChange();
        } else if(n.type==='ERROR') {
            if(this.voidedEvents.has(n.eventId)) return;
            const item=this.pending.find(p=>p.event.eventId===n.eventId);
            if(item) {item.status='rejected';item.error=n.message;this.persist();this.onPending();}
            this.onError(n.message);this.onChange();
        }
    }
    flagSuspension() {
        const status=this.state?.activeHeat?.status;
        if(status==='RUNNING') this.suspended=true;
        if(status==='ARMED') {if(this.frozen) this.setReady(false);this.frozen=null;}
        this.samples=[];this.best=null;this.onChange();
    }
    setReady(ready) {
        const heat=this.state?.activeHeat;
        if(!heat || heat.status!=='ARMED') return;
        if(ready && (!this.clockReady() || !this.connected())) throw new Error('Zeitabgleich noch nicht stabil. Bitte kurz warten.');
        this.frozen=ready?{heatId:heat.id,offset:this.best.offset,qualityMs:this.best.rtt/2,created:performance.now()}:null;
        if(ready) this.suspended=false;
        // WebSocket erhält die Reihenfolge: der Server sieht den frischen Abgleich vor READY.
        this.sync();this.send({type:'READY',heatId:heat.id,ready});this.onChange();
    }
    hasCalibration() {return this.frozen?.heatId===this.state?.activeHeat?.id;}
    estimatedNow() {
        const offset=this.frozen?.offset??this.best?.offset;
        return offset==null?(this.state?.serverNow??0)+(performance.now()-this.lastStatePerf):performance.now()+offset;
    }
    startRace(at) {
        const h=this.state?.activeHeat;
        if(!h || h.status!=='ARMED' || !this.hasCalibration() || !this.connected() || !this.clockReady() || this.started.has(h.id)) return;
        const required=['starter',...h.lanes.map(l=>'lane'+l.lane)];
        if(!required.every(r=>this.state.devices.some(d=>d.role===r&&d.online&&d.ready))) throw new Error('Es sind noch nicht alle Stationen bereit.');
        const e=this.event('START',at);this.started.set(h.id,e);this.saveEvent(e);
    }
    stopRace(at) {
        const h=this.state?.activeHeat;
        if(!h || h.status!=='RUNNING' || !this.hasCalibration() || this.stopped.has(h.id) || this.localEvent('STOP',h.id)) return;
        const e=this.event('STOP',at);this.stopped.set(h.id,e);this.saveEvent(e);
    }
    falseStart() {
        const h=this.state?.activeHeat;
        if(this.role!=='starter' || !h || !['RUNNING','FINISHED'].includes(h.status)) return;
        if(!this.connected()) throw new Error('Fehlstart konnte nicht übertragen werden. Verbindung zum Laptop prüfen.');
        if(this.pending.some(p=>p.event.type==='FALSE_START'&&p.event.heatId===h.id)) return;
        this.saveEvent({type:'FALSE_START',eventId:uuid(),heatId:h.id,bootId:this.state.bootId,
            atMs:this.estimatedNow(),qualityMs:0,needsReview:false,note:''});
    }
    event(type,at) {
        return {type,eventId:uuid(),heatId:this.state.activeHeat.id,bootId:this.state.bootId,atMs:at+this.frozen.offset,qualityMs:this.frozen.qualityMs,needsReview:this.suspended,note:this.suspended?'Browser war im Hintergrund, pausiert oder Uhrverhalten auffällig. Zeit prüfen.':''};
    }
    saveEvent(event) {
        this.pending.push({event,heatNumber:this.state.activeHeat.number,elapsedMs:event.type==='STOP'?Math.round(event.atMs-this.state.activeHeat.startMs):null,status:'pending',error:''});this.persist();this.onPending();this.onChange();this.flush();
    }
    localEvent(type,heatId) { return this.pending.find(p=>p.event.type===type && p.event.heatId===heatId)?.event || (type==='STOP'?this.stopped:this.started).get(heatId); }
    persist() {
        try {localStorage.setItem(this.pendingKey,JSON.stringify(this.pending));}
        catch {this.onError('Der Browser konnte die lokale Sicherung nicht schreiben. Seite offen lassen und das Ergebnis am Laptop kontrollieren.');}
    }
    flush() {
        if(this.ws?.readyState!==WebSocket.OPEN) return;
        for(const item of this.pending) if(item.status==='pending') this.send(item.event);
    }
    discard(eventId) {this.pending=this.pending.filter(p=>p.event.eventId!==eventId);this.persist();this.onPending();this.onChange();}
}
