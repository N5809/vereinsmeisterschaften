import {$,api,escapeHtml,toast} from './common.js';

let summary=null,preview=null,restoreFile=null;
const titles={participants:'Teilnehmer (inkl. Archiv)',heats:'Läufe',results:'Ergebnisse',disciplines:'Disziplinen',classes:'Wertungsklassen'};
function countsMarkup(counts){return Object.entries(titles).map(([key,label])=>`<div class="data-count"><strong>${counts[key]}</strong><span>${label}</span></div>`).join('');}
function restoreReady(){
    const possible=!!preview&&!!restoreFile&&summary?.empty&&summary?.changeAllowed;
    $('#restore-confirmation').disabled=!possible;
    $('#restore-button').disabled=!possible||$('#restore-confirmation').value!=='WIEDERHERSTELLEN';
    $('#restore-hint').textContent=!preview?'Datei auswählen und prüfen.':!summary?.empty?'Der Datenbestand ist nicht leer. Vor der Wiederherstellung sichern und über „Alles löschen“ zurücksetzen.':'Die Sicherung ist geprüft und kann in den leeren Datenbestand eingelesen werden.';
}
export function renderDataSummary(value){
    summary=value;$('#data-counts').innerHTML=countsMarkup(value.counts);
    $('#reset-open').disabled=!value.changeAllowed;
    $('#reset-hint').textContent=value.changeAllowed?'Teilnehmer, Läufe, Ergebnisse, Disziplinen, Wertungsklassen und Protokoll werden gelöscht.':'Zuerst den aktivierten oder laufenden Lauf abschließen oder abbrechen.';
    restoreReady();
}
export function initDataTools(onChanged){
    const keys=Object.keys(titles);
    const field=key=>$('#export-'+key);
    function selected(){return Object.fromEntries(keys.map(key=>[key,field(key).checked]));}
    function dependencies(){
        const results=field('results').checked,heats=field('heats').checked||results,people=field('participants').checked||heats;
        if(results)field('heats').checked=true;
        if(heats){field('participants').checked=true;field('disciplines').checked=true;}
        if(people)field('classes').checked=true;
        field('heats').disabled=results;field('participants').disabled=heats;field('disciplines').disabled=heats;field('classes').disabled=people;
        $('#export-button').disabled=!Object.values(selected()).some(Boolean);
    }
    keys.forEach(key=>field(key).addEventListener('change',dependencies));dependencies();
    $('#backup-export-form').addEventListener('submit',async e=>{
        e.preventDefault();const button=$('#export-button');button.disabled=true;
        try {
            const response=await fetch('/api/admin/data/export',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(selected())});
            if(!response.ok){const error=await response.json();throw new Error(error.message||'Export fehlgeschlagen.');}
            const blob=await response.blob(),url=URL.createObjectURL(blob),link=document.createElement('a');
            link.href=url;link.download=response.headers.get('Content-Disposition')?.match(/filename="([^"]+)"/)?.[1]||'vereinsmeisterschaft-sicherung.json';
            document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
            toast('Sicherungsdatei heruntergeladen. Sie kann über „Wiederherstellen“ eingelesen werden.');
        }catch(error){toast(error.message,true);}finally{dependencies();}
    });
    $('#backup-file').addEventListener('change',()=>{restoreFile=null;preview=null;$('#backup-preview').hidden=true;$('#restore-confirmation').value='';restoreReady();});
    $('#backup-preview-form').addEventListener('submit',async e=>{
        e.preventDefault();const file=$('#backup-file').files[0];if(!file)return;
        const button=$('#preview-button');button.disabled=true;preview=null;restoreFile=null;restoreReady();
        try {
            const data=new FormData();data.append('file',file);
            const result=await api('/admin/data/preview',{method:'POST',body:data});
            if($('#backup-file').files[0]!==file)return;
            preview=result;restoreFile=file;
            $('#backup-preview').innerHTML=`<h3>Geprüfte Sicherung</h3><p>Erstellt am ${escapeHtml(new Date(result.exportedAt).toLocaleString('de-DE'))}</p><div class="data-counts">${countsMarkup(result.contents)}</div><p class="small muted">${result.selection.results?'Mit Ergebniszeiten und historischen Versuchen.':'Ohne Ergebniszeiten. Gesicherte Läufe werden als geplante Läufe vorbereitet.'}</p>`;
            $('#backup-preview').hidden=false;restoreReady();
        }catch(error){$('#backup-preview').hidden=true;toast(error.message,true);}finally{button.disabled=false;}
    });
    $('#restore-confirmation').addEventListener('input',restoreReady);
    $('#restore-form').addEventListener('submit',async e=>{
        e.preventDefault();if(!restoreFile||!preview)return;
        const button=$('#restore-button');button.disabled=true;
        try {
            const data=new FormData();data.append('file',restoreFile);data.append('confirmation',$('#restore-confirmation').value);
            await api('/admin/data/restore',{method:'POST',body:data});
            preview=null;restoreFile=null;$('#backup-preview').hidden=true;$('#backup-preview-form').reset();$('#restore-form').reset();
            await onChanged();toast('Sicherung wiederhergestellt. Die Stationen warten auf den nächsten aktivierten Lauf.');
        }catch(error){toast(error.message,true);}finally{restoreReady();}
    });
    $('#reset-open').addEventListener('click',()=>{$('#reset-confirmation').value='';$('#reset-error').textContent='';$('#reset-submit').disabled=true;$('#reset-dialog').showModal();});
    $('#reset-cancel').addEventListener('click',()=>$('#reset-dialog').close());
    $('#reset-confirmation').addEventListener('input',()=>$('#reset-submit').disabled=$('#reset-confirmation').value!=='ALLES LÖSCHEN');
    $('#reset-form').addEventListener('submit',async e=>{
        e.preventDefault();const button=$('#reset-submit');button.disabled=true;
        try {
            await api('/admin/data/reset',{method:'POST',body:{confirmation:$('#reset-confirmation').value}});
            $('#reset-dialog').close();await onChanged();toast('Alle Wettkampfdaten gelöscht. Der Datenbestand ist jetzt leer.');
        }catch(error){$('#reset-error').textContent=error.message;button.disabled=false;}
    });
}
