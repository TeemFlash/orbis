import {build} from 'esbuild';
import {readFile,writeFile} from 'node:fs/promises';
const result=await build({entryPoints:['entry.js'],bundle:true,minify:true,write:false});
const html=(await readFile('template.html','utf8')).replace('__BUNDLE__',result.outputFiles[0].text);
await writeFile('../../app/src/main/assets/globe.html',html);
