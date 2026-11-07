import { execFileSync } from 'node:child_process';
import { readdirSync, mkdtempSync, rmSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { tmpdir } from 'node:os';

/** Run actual frozen Kotlin sources with Gradle, or a local Kotlin/JDK compiler. */
export function runJvmProbe({root,args,probe,task,input,output}) {
  const value=(flag,fallback)=>{const i=args.indexOf(flag);return i<0?fallback:args[i+1];};
  const kotlinc=value('--kotlinc',null);
  if(kotlinc) {
    const walk=dir=>readdirSync(dir,{withFileTypes:true}).flatMap(e=>
      e.isDirectory()?walk(join(dir,e.name)):e.name.endsWith('.kt')?[join(dir,e.name)]:[]);
    const temp=mkdtempSync(join(tmpdir(),'pursuit-paired-'));
    try {
      const sources=walk(resolve(root,'android-native/kotlin-engine/src/main'));
      sources.push(resolve(root,`android-native/kotlin-engine/src/test/java/io/github/extracteddeath/pursuitiron/rebuild/${probe}.kt`));
      const jar=join(temp,'probe.jar');
      execFileSync(kotlinc,[...sources,'-include-runtime','-d',jar],{cwd:root,stdio:'inherit'});
      execFileSync(value('--java','java'),['-cp',jar,`io.github.extracteddeath.pursuitiron.rebuild.${probe}`,input,output],
        {cwd:root,stdio:'inherit'});
    } finally {rmSync(temp,{recursive:true,force:true});}
  } else {
    const extra=JSON.parse(process.env.PURSUIT_COMPARISON_GRADLE_ARGS||'[]');
    if(!Array.isArray(extra)||!extra.every(v=>typeof v==='string'))throw Error('Invalid Gradle arguments');
    execFileSync(value('--gradle','gradle'),['-p',resolve(root,'android-native'),`:kotlin-engine:${task}`,
      `-PcomparisonInput=${input}`,`-PcomparisonOutput=${output}`,'--no-daemon','--console=plain','--max-workers=2',...extra],
      {cwd:root,stdio:'inherit'});
  }
}
