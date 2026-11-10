import createGlobe from 'cobe';
const canvas=document.querySelector('canvas');let phi=0,theta=.2,point=null,drag=null,velocity=0,running=true,frame,globe;
let tiltHorizontal=0,tiltVertical=0,shownHorizontal=0,shownVertical=0,lastFrame=0;
function size(){return Math.min(innerWidth,innerHeight-80)}
function init(){const width=size(),dpr=Math.min(devicePixelRatio,2);canvas.style.width=width+'px';canvas.style.height=width+'px';
try{globe=createGlobe(canvas,{width:width*dpr,height:width*dpr,devicePixelRatio:dpr,phi,theta,dark:0,diffuse:1.5,mapSamples:16000,mapBrightness:10,baseColor:[1,1,1],markerColor:[.3,.45,.85],glowColor:[.89,.98,.96],markers:[],markerElevation:.02,opacity:.85});animate();}catch(e){document.querySelector('#error').hidden=false;}}
function animate(now=performance.now()){if(!running)return;const dt=lastFrame?Math.min(100,now-lastFrame):16;lastFrame=now;if(!drag){phi+=.00045+velocity;velocity*=.95;}const alpha=1-Math.exp(-dt/120),targetH=drag?0:tiltHorizontal,targetV=drag?0:tiltVertical;shownHorizontal+=(targetH-shownHorizontal)*alpha;shownVertical+=(targetV-shownVertical)*alpha;globe.update({phi:phi+shownHorizontal,theta:Math.max(-1.2,Math.min(1.2,theta+shownVertical)),markers:point?[{location:point,size:.045}]:[]});frame=requestAnimationFrame(animate);}
canvas.onpointerdown=e=>{drag={x:e.clientX,y:e.clientY};shownHorizontal=shownVertical=0;velocity=0;canvas.setPointerCapture(e.pointerId)};
canvas.onpointermove=e=>{if(!drag)return;const dx=e.clientX-drag.x;phi+=dx/160;theta=Math.max(-1.2,Math.min(1.2,theta+(e.clientY-drag.y)/260));velocity=dx/500;drag={x:e.clientX,y:e.clientY};};
canvas.onpointerup=canvas.onpointercancel=()=>drag=null;
window.setPoint=(lat,lon)=>{point=[lat,lon];};
window.focusPoint=()=>{if(point){phi=Math.PI-(point[1]*Math.PI/180-Math.PI/2);theta=point[0]*Math.PI/180;velocity=0;}};
window.setDeviceTilt=(horizontal,vertical)=>{if(!Number.isFinite(horizontal)||!Number.isFinite(vertical)||!running){tiltHorizontal=tiltVertical=shownHorizontal=shownVertical=0;return;}const limit=.45;tiltHorizontal=Math.max(-limit,Math.min(limit,horizontal));tiltVertical=Math.max(-limit,Math.min(limit,vertical));if(tiltHorizontal===0&&tiltVertical===0)shownHorizontal=shownVertical=0;};
window.setRunning=value=>{if(running===value)return;running=value;cancelAnimationFrame(frame);if(!value){tiltHorizontal=tiltVertical=shownHorizontal=shownVertical=0;lastFrame=0;}else if(globe)animate();};
window.addEventListener('resize',()=>{cancelAnimationFrame(frame);if(globe)globe.destroy();init();});
init();
