const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {ref,set,get,query,orderByChild,limitToLast,onValue,serverTimestamp}=require('firebase/database');
const fs=require('fs');
const rules=fs.readFileSync(require('path').join(__dirname,'../database.rules.json'),'utf8');
(async()=>{
 const env=await initializeTestEnvironment({projectId:'demo-semey-map',database:{host:'127.0.0.1',port:9000,rules}});
 let passed=0;const ok=(name)=>console.log(`PASS ${++passed}: ${name}`);
 try {
  const a=env.authenticatedContext('alice').database(),b=env.authenticatedContext('bob').database(),anon=env.unauthenticatedContext().database();
  const signedAnon=env.authenticatedContext('livepulse-guest',{firebase:{sign_in_provider:'anonymous'}}).database();
  const recent=db=>query(ref(db,'messages'),orderByChild('createdAt'),limitToLast(100));
  const msg=(uid,text='Hello Semey')=>({uid,name:'Test explorer',text,createdAt:serverTimestamp()});
  await assertFails(get(recent(signedAnon)));ok('LivePulse anonymous session cannot read old chat');
  await assertFails(set(ref(signedAnon,'messages/livepulse'),msg('livepulse-guest')));ok('LivePulse session cannot write old chat');
  await assertFails(get(ref(signedAnon,'photos/photo')));ok('LivePulse session cannot read old photos');
  await assertFails(get(recent(anon)));ok('anonymous cannot read');
  await assertFails(set(ref(anon,'messages/anonymous'),msg('alice')));ok('anonymous cannot send');
  await assertFails(set(ref(a,'messages/spoof'),msg('bob')));ok('cannot spoof sender');
  await assertFails(set(ref(a,'messages/blank'),msg('alice','   ')));ok('blank message rejected');
  await assertFails(set(ref(a,'messages/long'),msg('alice','x'.repeat(1001))));ok('long message rejected');
  await assertFails(set(ref(a,'messages/extra'),{...msg('alice'),admin:true}));ok('extra fields rejected');
  await assertFails(set(ref(a,'messages/fake-time'),{...msg('alice'),createdAt:1}));ok('client timestamp rejected');
  await assertSucceeds(set(ref(a,'messages/valid'),msg('alice')));ok('authenticated message accepted');
  await assertSucceeds(set(ref(a,'messages/multiline'),msg('alice','Hello\nСемей 👋')));ok('multiline Unicode accepted');
  await assertFails(set(ref(a,'messages/valid'),msg('alice','edited')));ok('message overwrite rejected');
  await assertFails(set(ref(a,'messages/valid'),null));ok('message deletion rejected');
  await assertFails(get(ref(a,'messages')));ok('unbounded history rejected');
  await assertSucceeds(get(recent(b)));ok('second user reads shared room');
  await new Promise((resolve,reject)=>{
   const timeout=setTimeout(()=>{off();reject(Error('Realtime event timed out'));},10000);
   const off=onValue(recent(b),snap=>{if(snap.hasChild('live')){clearTimeout(timeout);off();resolve();}},reject);
   set(ref(a,'messages/live'),msg('alice','Realtime test')).catch(reject);
  });ok('second client receives new message without polling');
  const photo={uid:'alice',data:'/9j/AA=='};
  await assertFails(set(ref(anon,'photos/p'),photo));ok('anonymous photo upload denied');
  await assertFails(set(ref(a,'photos/spoof'),{...photo,uid:'bob'}));ok('photo sender spoof rejected');
  await assertFails(set(ref(a,'photos/oversize'),{...photo,data:'/9j/'+ 'A'.repeat(136537)}));ok('oversize photo rejected');
  await assertSucceeds(set(ref(a,'photos/photo'),photo));ok('small encoded photo accepted');
  await assertFails(get(ref(anon,'photos/photo')));ok('anonymous photo download denied');
  await assertSucceeds(get(ref(b,'photos/photo')));ok('authenticated photo download allowed');
  await assertSucceeds(set(ref(a,'messages/photo'),{...msg('alice',''),photoId:'photo'}));ok('photo without caption accepted');
  await assertFails(set(ref(a,'messages/mismatch'),{...msg('alice'),photoId:'photo'}));ok('mismatched attachment rejected');
  console.log(`ALL ${passed} RULES / REALTIME CHECKS PASSED`);
 } finally {await env.cleanup();}
})().catch(e=>{console.error(e);process.exitCode=1;});
