import {pathToFileURL} from 'node:url';
const root=new URL('../vendor/', import.meta.url);
export function resolve(specifier, context, next) {
 const map={'react':'react.js','react/jsx-runtime':'react-jsx-runtime.js','react-dom/client':'react-dom-client.js','lucide-react':'lucide-react.js'};
 return map[specifier]?{url:new URL(map[specifier],root).href,shortCircuit:true}:next(specifier,context);
}
