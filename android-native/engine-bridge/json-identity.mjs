/** Environment-neutral JSON identity. No Node, DOM, crypto host or I/O access. */
export function utf8ByteLength(text) {
  let count=0;
  for (let i=0;i<text.length;i++) {
    const cp=text.codePointAt(i);
    if (cp>0xffff) i++;
    count+=cp<0x80?1:cp<0x800?2:cp<0x10000?3:4;
  }
  return count;
}

// SHA-256 for content identity only. Fixed vectors and differential tests use
// Node's independent native crypto implementation, including UTF-8 edge cases.
const K=new Uint32Array([
  0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
  0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
  0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
  0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
  0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
  0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
  0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
  0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
]);
const rotate=(v,n)=>(v>>>n)|(v<<(32-n));
export function sha256Utf8(text) {
  if (typeof text!=='string') throw new TypeError('String identity required');
  const state=new Uint32Array([0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,
    0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19]);
  const block=new Uint8Array(64), words=new Uint32Array(64);
  let used=0,bytes=0;
  function compress() {
    for (let i=0;i<16;i++) {
      const p=i*4;
      words[i]=(block[p]<<24)|(block[p+1]<<16)|(block[p+2]<<8)|block[p+3];
    }
    for (let i=16;i<64;i++) {
      const a=words[i-15],b=words[i-2];
      words[i]=(words[i-16]+(rotate(a,7)^rotate(a,18)^(a>>>3))+
        words[i-7]+(rotate(b,17)^rotate(b,19)^(b>>>10)))>>>0;
    }
    let [a,b,c,d,e,f,g,h]=state;
    for (let i=0;i<64;i++) {
      const t1=(h+(rotate(e,6)^rotate(e,11)^rotate(e,25))+((e&f)^(~e&g))+K[i]+words[i])|0;
      const t2=((rotate(a,2)^rotate(a,13)^rotate(a,22))+((a&b)^(a&c)^(b&c)))|0;
      h=g;g=f;f=e;e=(d+t1)|0;d=c;c=b;b=a;a=(t1+t2)|0;
    }
    const round=[a,b,c,d,e,f,g,h];
    for (let i=0;i<8;i++) state[i]=(state[i]+round[i])>>>0;
  }
  function put(byte) {
    block[used++]=byte;
    if (used===64) { compress();used=0; }
  }
  function sourceByte(byte) { put(byte);bytes++; }
  try {
    for (let i=0;i<text.length;i++) {
      let cp=text.codePointAt(i);
      if (cp>0xffff) i++;
      // Match UTF-8's replacement of an unpaired UTF-16 surrogate.
      if (cp>=0xd800 && cp<=0xdfff) cp=0xfffd;
      if (cp<0x80) sourceByte(cp);
      else if (cp<0x800) { sourceByte(0xc0|(cp>>>6));sourceByte(0x80|(cp&63)); }
      else if (cp<0x10000) {
        sourceByte(0xe0|(cp>>>12));sourceByte(0x80|((cp>>>6)&63));sourceByte(0x80|(cp&63));
      } else {
        sourceByte(0xf0|(cp>>>18));sourceByte(0x80|((cp>>>12)&63));
        sourceByte(0x80|((cp>>>6)&63));sourceByte(0x80|(cp&63));
      }
    }
    const high=Math.floor(bytes/0x20000000),low=(bytes*8)>>>0;
    put(0x80);
    while (used!==56) put(0);
    for (const part of [high,low])
      for (const shift of [24,16,8,0]) put((part>>>shift)&255);
    return Array.from(state,v=>v.toString(16).padStart(8,'0')).join('');
  } finally { block.fill(0);words.fill(0);state.fill(0); }
}

/** JSON-only equality; types, missing fields, array order and -0 remain distinct. */
export function equalJson(a,b,depth=0) {
  if (Object.is(a,b)) return true;
  if (depth>64 || a===null || b===null || typeof a!=='object' || typeof b!=='object') return false;
  if (Array.isArray(a)!==Array.isArray(b)) return false;
  const keys=Object.keys(a);
  return keys.length===Object.keys(b).length && keys.every(key=>
    Object.prototype.hasOwnProperty.call(b,key) && equalJson(a[key],b[key],depth+1));
}
