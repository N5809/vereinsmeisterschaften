import {api, destination} from './common.js';
const role=document.querySelector('#role');
const requested=new URLSearchParams(location.search).get('role');
if([...role.options].some(o=>o.value===requested)) role.value=requested;
document.querySelector('#login-form').addEventListener('submit',async e=>{
    e.preventDefault(); const button=e.currentTarget.querySelector('button'); button.disabled=true;
    try { const me=await api('/login',{method:'POST',body:{role:role.value,code:document.querySelector('#code').value}}); location.href=destination(me.role); }
    catch(error) { document.querySelector('#login-error').textContent=error.message; }
    finally { button.disabled=false; }
});
