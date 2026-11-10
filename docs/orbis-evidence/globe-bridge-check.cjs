const fs=require('fs'),vm=require('vm'),assert=require('assert');
let next,latest;const canvas={style:{},setPointerCapture(){}};
const c={console,Math,Number,innerWidth:400,innerHeight:800,devicePixelRatio:1,performance:{now:()=>1},document:{querySelector:s=>s==='canvas'?canvas:{hidden:true}},requestAnimationFrame:f=>(next=f,1),cancelAnimationFrame(){},createGlobe:()=>({update:v=>latest=v,destroy(){}})};c.window=c;c.addEventListener=()=>{};
vm.createContext(c);vm.runInContext(fs.readFileSync('tools/globe/entry.js','utf8').replace("import createGlobe from 'cobe';",''),c);
let t=100;function frames(n=30){for(let i=0;i<n;i++)next(t+=16);}
c.setDeviceTilt(.3,.3);frames();assert(latest.theta>.45);
c.setDeviceTilt(-.3,-.3);frames();assert(latest.theta<0);
canvas.onpointerdown({clientX:0,clientY:0,pointerId:1});canvas.onpointermove({clientX:50,clientY:40});frames();assert(Math.abs(latest.theta-(.2+40/260))<.001);canvas.onpointerup();
c.setRunning(false);c.setDeviceTilt(.4,.4);c.setRunning(true);assert(Math.abs(latest.theta-(.2+40/260))<.001);
c.setDeviceTilt(NaN,Infinity);frames();assert(Number.isFinite(latest.phi)&&Number.isFinite(latest.theta));
console.log('PASS: globe tilt signs, touch priority, pause/reset and invalid input');
