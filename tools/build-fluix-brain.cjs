// Pixel Brain anatomical reference + AE2 Fluix palette.
// Runtime geometry, Blockbench source and previews are built from the same data.
const fs=require('fs'),path=require('path'),zlib=require('zlib'),crypto=require('crypto'),assert=require('assert');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'src/main/resources/assets/thaumicenergistics_ce'),doc=path.join(root,'doc/fluix-brain-model');
fs.mkdirSync(doc,{recursive:true});
const size=128,scale=.65,atlas=Buffer.alloc(size*size*4),glow=Buffer.alloc(size*size*4),elements=[];
const palette=['141223','1a172f','212040','262b54','373b72','6054a6','915dcd','b06fdd','ff80d7'];
function cube(name,from,to,material='cortex',rotation=[0,0,0],origin=[0,0,0]){elements.push({name,from,to,material,rotation,origin,faces:{}});}
for(const side of [-1,1]){
 const add=(name,a,b,material='cortex',rotation=[0,0,0],origin=[0,0,0])=>cube((side<0?'left_':'right_')+name,side<0?[-b[0],a[1],a[2]]:a,side<0?[-a[0],b[1],b[2]]:b,material,[rotation[0],rotation[1]*side,rotation[2]*side],[origin[0]*side,origin[1],origin[2]]);
 // Two full cerebral hemispheres, without the old stacked lid/crown layers.
 add('cerebrum',[.3,-1.8,-4.8],[4,4.5,4.6]);
 add('temporal_lobe',[3.65,-2.3,-2.8],[5.25,1.7,4.1],'cortex',[-8,0,-12],[3.8,.5,.5]);
 add('cerebellum',[.2,-3.6,1.1],[2.9,-1.5,4.4],'cerebellum',[-12,0,0],[0,-1.5,2.2]);
 // A thin implanted wafer and one small projecting crystal per hemisphere.
 // Dark ceramic and light pins, instead of large silver square buttons.
 const z=side>0?.25:-2.2;
 add('neural_wafer',[4.015,2.25,z],[4.20,3.35,z+2.7],'wafer');
 add('fluix_contact',[4.20,2.40,z+.75],[4.55,3.15,z+1.55],'crystal');
}
cube('corpus_callosum',[-.8,-2.6,-2.5],[.8,-1.2,2.0],'nerve');
cube('brainstem',[-.8,-5.5,.6],[.8,-2.2,2.2],'nerve',[25,0,0],[0,-2.2,1.4]);
const directions=['north','east','south','west','up','down'];
const normals={north:[0,0,-1],east:[1,0,0],south:[0,0,1],west:[-1,0,0],up:[0,1,0],down:[0,-1,0]};
function vertices(e,s){const [x,y,z]=e.from,[X,Y,Z]=e.to;return {north:[[X,Y,z],[X,y,z],[x,y,z],[x,Y,z]],south:[[x,Y,Z],[x,y,Z],[X,y,Z],[X,Y,Z]],east:[[X,Y,Z],[X,y,Z],[X,y,z],[X,Y,z]],west:[[x,Y,z],[x,y,z],[x,y,Z],[x,Y,Z]],up:[[x,Y,z],[x,Y,Z],[X,Y,Z],[X,Y,z]],down:[[x,y,Z],[x,y,z],[X,y,z],[X,y,Z]]}[s];}
function rotate(p,angles){let [x,y,z]=p;for(let i=0;i<3;i++){const a=angles[i]*Math.PI/180,c=Math.cos(a),s=Math.sin(a);if(i===0)[y,z]=[y*c-z*s,y*s+z*c];if(i===1)[x,z]=[x*c+z*s,-x*s+z*c];if(i===2)[x,y]=[x*c-y*s,x*s+y*c];}return [x,y,z];}
function transform(p,e){return rotate(p.map((v,i)=>v-e.origin[i]),e.rotation).map((v,i)=>v+e.origin[i]);}
// Center the complete rotated anatomy, including the tilted stem and side lobes.
// The jar renderer rotates around its liquid center, so the asset origin must
// describe that center rather than the cerebral hemispheres' construction grid.
const boundsMin=[Infinity,Infinity,Infinity],boundsMax=[-Infinity,-Infinity,-Infinity];
for(const e of elements)for(const side of directions)for(const p of vertices(e,side)){
 const point=transform(p,e);
 point.forEach((v,i)=>{boundsMin[i]=Math.min(boundsMin[i],v);boundsMax[i]=Math.max(boundsMax[i],v);});
}
const constructionCenter=boundsMin.map((v,i)=>(v+boundsMax[i])/2);
for(const e of elements)for(const key of ['from','to','origin'])e[key]=e[key].map((v,i)=>v-constructionCenter[i]);
function set(buf,x,y,hex){const c=[...Buffer.from(hex,'hex'),255];for(let k=0;k<4;k++)buf[(y*size+x)*4+k]=c[k];}
function line(points,put){for(let i=1;i<points.length;i++){let [x,y]=points[i-1], [X,Y]=points[i];const dx=Math.abs(X-x),dy=-Math.abs(Y-y),sx=x<X?1:-1,sy=y<Y?1:-1;let err=dx+dy;while(true){put(x,y);if(x===X&&y===Y)break;const n=2*err;if(n>=dy){err+=dy;x+=sx;}if(n<=dx){err+=dx;y+=sy;}}}}
let cursorX=1,cursorY=1,rowHeight=0;
function tile(e,side){
 const delta=e.to.map((v,i)=>v-e.from[i]);
 const [a,b]=side==='up'||side==='down'?[delta[0],delta[2]]:side==='east'||side==='west'?[delta[2],delta[1]]:[delta[0],delta[1]];
 const w=Math.max(2,Math.round(a*2)),h=Math.max(2,Math.round(b*2));
 if(cursorX+w+1>=size){cursorX=1;cursorY+=rowHeight+2;rowHeight=0;}assert(cursorY+h+1<size,'Atlas overflow');
 const tx=cursorX,ty=cursorY;cursorX+=w+2;rowHeight=Math.max(rowHeight,h);
 const grid=Array.from({length:h},()=>Array(w)),lit=Array.from({length:h},()=>Array(w).fill(false));
 for(let y=0;y<h;y++)for(let x=0;x<w;x++)grid[y][x]=palette[4];
 const put=(x,y,c,em=false)=>{if(x>=0&&y>=0&&x<w&&y<h){grid[y][x]=c;lit[y][x]=em;}};
 if(e.material==='cortex'){
   // Painted pixel clusters: broad gyri separated by sparse dark grooves.
   // Each surface has its own composition; no repeated noise or maze stamps.
   const front=['55543355','56543365','55445565','44356554','33565543','45555433','56543345','56543365','55445565','44356554','33555443','34443223','44443344'];
   const flank=['5555434555554335555','5655434566654335655','5655444555554445655','5544555544335555554','4435655433356655443','3356655434555544333','4555544335654434554','5654434555654335654','5654335655544456654','5544456554335555543','4435655543356654433','3355544334555543334','4444333344444333444'];
   const top=['55433555','56433565','56445565','55565554','44555443','33554335','35554336','56554446','56555555','55443554','44333543','45545543','56565544','56555455','55443356','44333565','34445554','45555443','55544334'];
   let stencil=side==='up'||side==='down'?top:side==='north'||side==='south'?front:flank;
   const flip=e.name.startsWith('left') !== (side==='south'||side==='west');
   for(let y=0;y<h;y++)for(let x=0;x<w;x++){
     let sx=Math.min(stencil[0].length-1,Math.floor(x*stencil[0].length/w));
     let sy=Math.min(stencil.length-1,Math.floor(y*stencil.length/h));
     if(flip)sx=stencil[0].length-1-sx;
     let value=Number(stencil[sy][sx]);
     if(e.name.endsWith('temporal_lobe'))value=Math.max(2,value-1);
     if(side==='down')value=Math.max(1,value-2);
     put(x,y,palette[value]);
   }
   const outer=side===(e.name.startsWith('left')?'west':'east');
   if(e.name.endsWith('cerebrum')&&outer){
     // Two short conductive branches near the implant; tissue remains dominant.
     const left=e.name.startsWith('left'),u=Math.round(w*(left?.32:.64));
     line([[u,2],[u+2,2],[u+2,5],[u+4,5]],(x,y)=>put(x,y,palette[6]));
     line([[u-2,2],[u-2,0]],(x,y)=>put(x,y,palette[5]));
     put(u+4,5,palette[7]);
   }
 } else if(e.material==='cerebellum'){
   for(let y=0;y<h;y++)for(let x=0;x<w;x++)put(x,y,palette[y%3===1?2:y%3===0?5:4]);
 } else if(e.material==='nerve'){
   for(let y=0;y<h;y++)for(let x=0;x<w;x++)put(x,y,palette[x===0?3:x===w-1?2:y<h/3?5:4]);
 } else if(e.material==='wafer'){
   for(let y=0;y<h;y++)for(let x=0;x<w;x++)put(x,y,y===0?'4d4d67':'222433');
   if(side==='east'||side==='west')for(let x=0;x<w;x+=2)put(x,h-1,'9a9fb4');
 } else if(e.material==='crystal'){
   for(let y=0;y<h;y++)for(let x=0;x<w;x++)put(x,y,palette[side==='up'?7:x===0?6:4],side==='up');
   if(side==='east'||side==='west')put(0,0,palette[8],true);
 }
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){set(atlas,tx+x,ty+y,grid[y][x]);if(lit[y][x])set(glow,tx+x,ty+y,grid[y][x]);}
 for(let y=-1;y<=h;y++)for(let x=-1;x<=w;x++)if(x<0||x>=w||y<0||y>=h)set(atlas,tx+x,ty+y,grid[Math.max(0,Math.min(h-1,y))][Math.max(0,Math.min(w-1,x))]);
 return {uv:[tx,ty,tx+w,ty+h],texture:'#cortex'};
}
for(const e of elements)for(const side of directions)e.faces[side]=tile(e,side);
function crc(buf){let c=~0;for(const b of buf){c^=b;for(let k=0;k<8;k++)c=(c>>>1)^((c&1)?0xedb88320:0);}return(c^~0)>>>0;}
function chunk(type,data){const t=Buffer.from(type),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(data.length);c.writeUInt32BE(crc(Buffer.concat([t,data])));return Buffer.concat([n,t,data,c]);}
function png(w,h,rgba){const head=Buffer.alloc(13);head.writeUInt32BE(w,0);head.writeUInt32BE(h,4);head[8]=8;head[9]=6;const raw=Buffer.alloc(h*(w*4+1));for(let y=0;y<h;y++)rgba.copy(raw,y*(w*4+1)+1,y*w*4,(y+1)*w*4);return Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',head),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
const texture=png(size,size,atlas),emissive=png(size,size,glow),texDir=path.join(assets,'textures/entity');fs.mkdirSync(texDir,{recursive:true});
fs.writeFileSync(path.join(texDir,'fluix_brain.png'),texture);fs.writeFileSync(path.join(texDir,'fluix_brain_emissive.png'),emissive);
const model={credit:'TECE Fluix Brain; anatomical reference: Pixel Brain by sparkskye (CC BY 4.0); geometry redrawn, AE2-inspired palette and implants added',texture_size:[size,size],scale,textures:{cortex:'thaumicenergistics_ce:textures/entity/fluix_brain.png',emissive:'thaumicenergistics_ce:textures/entity/fluix_brain_emissive.png'},elements};
fs.writeFileSync(path.join(assets,'models/entity/fluix_brain.json'),JSON.stringify(model,null,2)+'\n');
const bbElements=elements.map(e=>({...e,type:'cube',uuid:crypto.randomUUID(),box_uv:false,faces:Object.fromEntries(Object.entries(e.faces).map(([s,f])=>[s,{uv:f.uv,texture:0}]))}));
fs.writeFileSync(path.join(doc,'fluix_brain.bbmodel'),JSON.stringify({meta:{format_version:'4.10',model_format:'free',box_uv:false},name:'Fluix Brain - anatomical rebuild',credit:model.credit,resolution:{width:size,height:size},elements:bbElements,outliner:bbElements.map(e=>e.uuid),textures:[{name:'fluix_brain.png',id:'cortex',uuid:crypto.randomUUID(),width:size,height:size,uv_width:size,uv_height:size,source:'data:image/png;base64,'+texture.toString('base64')}]},null,2));
// Actual geometry preview: depth-tested nearest-neighbor texture rendering.
function preview(filename,direction){
const W=960,H=720,output=Buffer.alloc(W*H*4),depth=new Float64Array(W*H).fill(-Infinity);
for(let i=0;i<W*H;i++)output.set([91,89,95,255],i*4);
const length=Math.hypot(...direction),view=direction.map(v=>v/length);
const horizontal=Math.hypot(view[0],view[2]),right=[-view[2]/horizontal,0,view[0]/horizontal];
const up=[-view[1]*right[2],view[0]*right[2]-view[2]*right[0],view[1]*right[0]];
function dot(a,b){return a.reduce((s,v,i)=>s+v*b[i],0);}
function project(p){return [W/2+dot(p,right)*40,H/2-dot(p,up)*40,dot(p,view)];}
for(const e of elements)for(const side of directions){
 const n=rotate(normals[side],e.rotation);if(dot(n,view)<=0)continue;const pts=vertices(e,side).map(p=>project(transform(p,e))),uv=e.faces[side].uv;
 const tex=[[uv[0],uv[1]],[uv[0],uv[3]],[uv[2],uv[3]],[uv[2],uv[1]]];
 for(const ids of [[0,1,2],[0,2,3]]){
   const [a,b,c]=ids.map(i=>pts[i]),den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);if(Math.abs(den)<.001)continue;
   for(let y=Math.max(0,Math.floor(Math.min(a[1],b[1],c[1])));y<=Math.min(H-1,Math.ceil(Math.max(a[1],b[1],c[1])));y++)for(let x=Math.max(0,Math.floor(Math.min(a[0],b[0],c[0])));x<=Math.min(W-1,Math.ceil(Math.max(a[0],b[0],c[0])));x++){
     const t=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/den,u=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/den,v=1-t-u;
     if(t<0||u<0||v<0)continue;const d=t*a[2]+u*b[2]+v*c[2],i=y*W+x;if(d<=depth[i])continue;depth[i]=d;
     const tx=Math.min(uv[2]-1,Math.max(uv[0],Math.floor(t*tex[ids[0]][0]+u*tex[ids[1]][0]+v*tex[ids[2]][0]))),ty=Math.min(uv[3]-1,Math.max(uv[1],Math.floor(t*tex[ids[0]][1]+u*tex[ids[1]][1]+v*tex[ids[2]][1])));
     const pixel=(ty*size+tx)*4,shade=glow[pixel+3]?1:n[1]>0?1.08:n[0]>.5?.82:.94;
     for(let k=0;k<3;k++)output[i*4+k]=Math.min(255,Math.round(atlas[pixel+k]*shade));
   }
 }
}
fs.writeFileSync(path.join(doc,filename),png(W,H,output));
}
preview('ingame-model-preview.png',[.65,.15,-.72]);
preview('model-back-preview.png',[-.65,.15,.72]);
preview('model-top-preview.png',[.58,.65,-.66]);
for(const e of elements){
 for(const side of directions)for(const p of vertices(e,side)){
   const [x,y,z]=transform(p,e);
   assert(Math.hypot(x,z)*scale/16<.30,'Rotated brain crosses jar: '+e.name);
   assert(13/16+y*scale/16-.0125>.52&&13/16+y*scale/16+.0125<1.06,'Brain leaves liquid: '+e.name);
 }
 for(const f of Object.values(e.faces))assert(f.uv.every(v=>v>=0&&v<=size),'UV outside atlas');
}
assert.equal(elements.length,12);assert(glow.some(v=>v>0));
console.log('PASS: 8 anatomical cuboids + 4 implant pieces; 2 texels/model unit; rotated geometry, UV bounds, emissive mask and jar clearance.');
console.log('Construction origin corrected by model units: '+JSON.stringify(constructionCenter));
