const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const {initializeTestEnvironment, assertFails, assertSucceeds} = require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,query,orderBy,limit,updateDoc,deleteDoc,serverTimestamp,Timestamp,onSnapshot} = require('firebase/firestore');
(async () => {
 const env = await initializeTestEnvironment({projectId:'demo-livepulse',firestore:{host:'127.0.0.1',port:8088,rules:fs.readFileSync(path.join(__dirname,'../../firestore.rules'),'utf8')}});
 let passed=0;
 const check = async(name, fn) => { await fn(); passed++; console.log('PASS '+name); };
 try {
  await env.clearFirestore();
  const anon = uid => env.authenticatedContext(uid,{firebase:{sign_in_provider:'anonymous'}}).firestore();
  const alice=anon('alice'), bob=anon('bob'), guest=env.unauthenticatedContext().firestore();
  const message=(id,extra={})=>({id,text:'Hello',authorId:'alice',timestamp:serverTimestamp(),...extra});
  await check('guest read denied',()=>assertFails(getDocs(collection(guest,'messages'))));
  await check('guest create denied',()=>assertFails(setDoc(doc(guest,'messages','guest'),message('guest'))));
  await check('anonymous create accepted',()=>assertSucceeds(setDoc(doc(alice,'messages','valid'),message('valid'))));
  await check('other anonymous user reads',()=>assertSucceeds(getDoc(doc(bob,'messages','valid'))));
  await check('spoofed UID denied',()=>assertFails(setDoc(doc(bob,'messages','spoof'),message('spoof'))));
  await check('wrong id denied',()=>assertFails(setDoc(doc(alice,'messages','id'),message('wrong'))));
  await check('client timestamp denied',()=>assertFails(setDoc(doc(alice,'messages','time'),message('time',{timestamp:Timestamp.fromMillis(1)}))));
  await check('blank text denied',()=>assertFails(setDoc(doc(alice,'messages','blank'),message('blank',{text:'   '}))));
  await check('empty text denied',()=>assertFails(setDoc(doc(alice,'messages','empty'),message('empty',{text:''}))));
  await check('long text denied',()=>assertFails(setDoc(doc(alice,'messages','long'),message('long',{text:'a'.repeat(1001)}))));
  await check('extra field denied',()=>assertFails(setDoc(doc(alice,'messages','extra'),message('extra',{photo:'no'}))));
  await check('update denied',()=>assertFails(updateDoc(doc(alice,'messages','valid'),{text:'edit'})));
  await check('delete denied',()=>assertFails(deleteDoc(doc(alice,'messages','valid'))));
  const password=env.authenticatedContext('password',{firebase:{sign_in_provider:'password'}}).firestore();
  await check('multiline text accepted',()=>assertSucceeds(setDoc(doc(alice,'messages','multiline'),message('multiline',{text:'Line one\nLine two'}))));
  await check('other provider denied',()=>assertFails(getDocs(collection(password,'messages'))));
  await check('two clients realtime server timestamp',async()=>{
   const observed=new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>{unsub(); reject(Error('snapshot timeout'));},10000);
    const unsub=onSnapshot(query(collection(bob,'messages'),orderBy('timestamp','desc')),snap=>{
     const found=snap.docs.find(d=>d.id==='realtime');
     if(found){clearTimeout(timeout);unsub();resolve(found.data());}
    },reject);
   });
   await setDoc(doc(alice,'messages','realtime'),message('realtime'));
   const result=await observed; assert.equal(result.authorId,'alice');assert.ok(result.timestamp instanceof Timestamp);
  });
  console.log(`${passed} checks passed`);
 } finally { await env.cleanup(); }
})().catch(error=>{console.error(error);process.exitCode=1;});
