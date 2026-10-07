import { generateNextProgramForShell, shell } from '../index.js';
const config={name:'Isolated audit',unit:'lb',goal:'both',experience:'intermediate',split:'upper_lower',days:4,session:'s60',weeks:6,progression:'auto',deload:false,equipment:['barbell','rack','bench','dumbbell','cable','machine','smith','pullup','dip','legpress','hacksquat','legext','legcurl','calfmachine'],focus:{},reduce:[],barbellCap:3,noBodyweight:false,noSupersets:false};
const result=generateNextProgramForShell({config,legacyExercises:shell.EXERCISES,seed:223,makeId:()=> 'isolated-223'});
console.log(JSON.stringify(result,null,2));
