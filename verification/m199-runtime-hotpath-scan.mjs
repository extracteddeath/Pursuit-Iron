import fs from 'node:fs';
const app = fs.readFileSync('modules/App.js','utf8');

function around(needle, radius=2400) {
  const hits=[]; let p=0;
  while ((p=app.indexOf(needle,p))>=0) {
    hits.push({line:app.slice(0,p).split('\n').length,text:app.slice(Math.max(0,p-radius),Math.min(app.length,p+needle.length+radius))});
    p+=needle.length;
  }
  return hits;
}

const report={
  appBytes: Buffer.byteLength(app),
  shadowAttach: around('attachShadowEvaluation('),
  shadowProgram: around('runShadowProgramForShell('),
  shadowCycle: around('attachShadowToCycleBuild('),
  shadowReads: ['nextEngine.shadow','nextEngine?.shadow','.shadow?.','shadow?.'].flatMap(x=>around(x,900)),
  forceTickUses: around('forceTick',2200),
  timerTextRefUses: around('timerTextRef',2200),
  elapsedUses: around('runElapsedMs()',2200),
  oneSecondIntervals: around('setInterval(() => forceTick(n => n + 1), 1000)',5000),
  halfSecondIntervals: around('setInterval(',900).filter(x=>x.text.includes('500')),
  saveStoreUses: around('saveStore(',2200),
  storeStringify: around('text = JSON.stringify(data)',1800),
  livePersistUses: around('persistLive()',1800),
  storageWrites: around('localStorage.setItem(',700)
};
fs.writeFileSync('m199-runtime-hotpath-scan.json',JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
