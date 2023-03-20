"use strict";(self.webpackChunk=self.webpackChunk||[]).push([[5367],{3905:(e,t,n)=>{n.r(t),n.d(t,{MDXContext:()=>p,MDXProvider:()=>d,mdx:()=>v,useMDXComponents:()=>u,withMDXComponents:()=>s});var a=n(67294);function r(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function o(){return o=Object.assign||function(e){for(var t=1;t<arguments.length;t++){var n=arguments[t];for(var a in n)Object.prototype.hasOwnProperty.call(n,a)&&(e[a]=n[a])}return e},o.apply(this,arguments)}function c(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);t&&(a=a.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,a)}return n}function l(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?c(Object(n),!0).forEach((function(t){r(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):c(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function i(e,t){if(null==e)return{};var n,a,r=function(e,t){if(null==e)return{};var n,a,r={},o=Object.keys(e);for(a=0;a<o.length;a++)n=o[a],t.indexOf(n)>=0||(r[n]=e[n]);return r}(e,t);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);for(a=0;a<o.length;a++)n=o[a],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(r[n]=e[n])}return r}var p=a.createContext({}),s=function(e){return function(t){var n=u(t.components);return a.createElement(e,o({},t,{components:n}))}},u=function(e){var t=a.useContext(p),n=t;return e&&(n="function"==typeof e?e(t):l(l({},t),e)),n},d=function(e){var t=u(e.components);return a.createElement(p.Provider,{value:t},e.children)},m="mdxType",h={inlineCode:"code",wrapper:function(e){var t=e.children;return a.createElement(a.Fragment,{},t)}},f=a.forwardRef((function(e,t){var n=e.components,r=e.mdxType,o=e.originalType,c=e.parentName,p=i(e,["components","mdxType","originalType","parentName"]),s=u(n),d=r,m=s["".concat(c,".").concat(d)]||s[d]||h[d]||o;return n?a.createElement(m,l(l({ref:t},p),{},{components:n})):a.createElement(m,l({ref:t},p))}));function v(e,t){var n=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var o=n.length,c=new Array(o);c[0]=f;var l={};for(var i in t)hasOwnProperty.call(t,i)&&(l[i]=t[i]);l.originalType=e,l[m]="string"==typeof e?e:r,c[1]=l;for(var p=2;p<o;p++)c[p]=n[p];return a.createElement.apply(null,c)}return a.createElement.apply(null,n)}f.displayName="MDXCreateElement"},35270:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>s,contentTitle:()=>i,default:()=>h,frontMatter:()=>l,metadata:()=>p,toc:()=>u});var a=n(87462),r=n(63366),o=(n(67294),n(3905)),c=["components"],l={id:"cached-values",title:"Cached Values"},i=void 0,p={unversionedId:"cached-values",id:"cached-values",title:"Cached Values",description:"The purpose of the Cached Values API is to provide caching within Spec classes, rather than have to repeatedly make an expensive calculation or to use lazy state updates for this purpose.",source:"@site/../docs/cached-values.md",sourceDirName:".",slug:"/cached-values",permalink:"/docs/cached-values",draft:!1,editUrl:"https://github.com/facebook/litho/edit/master/website/../docs/cached-values.md",tags:[],version:"current",frontMatter:{id:"cached-values",title:"Cached Values"}},s={},u=[],d={toc:u},m="wrapper";function h(e){var t=e.components,n=(0,r.Z)(e,c);return(0,o.mdx)(m,(0,a.Z)({},d,n,{components:t,mdxType:"MDXLayout"}),(0,o.mdx)("p",null,"The purpose of the Cached Values API is to provide caching within ",(0,o.mdx)("inlineCode",{parentName:"p"},"Spec")," classes, rather than have to repeatedly make an expensive calculation or to use lazy state updates for this purpose."),(0,o.mdx)("p",null,"The API is made up of two annotations: ",(0,o.mdx)("inlineCode",{parentName:"p"},"@CachedValue")," and ",(0,o.mdx)("inlineCode",{parentName:"p"},"@OnCalculateCachedValue"),". ",(0,o.mdx)("inlineCode",{parentName:"p"},"@CachedValue")," is used in the same way as ",(0,o.mdx)("inlineCode",{parentName:"p"},"@Prop"),", ",(0,o.mdx)("inlineCode",{parentName:"p"},"@State")," etc - it is used to annotate a parameter to a ",(0,o.mdx)("inlineCode",{parentName:"p"},"Spec")," method, which the generated code will use to pass the ",(0,o.mdx)("inlineCode",{parentName:"p"},"Spec")," method the correct cached value. In particular, the generated code will check to see if the value is already cached, and if not it will calculate the value and cache it."),(0,o.mdx)("p",null,(0,o.mdx)("inlineCode",{parentName:"p"},"@OnCalculateCachedValue")," is used to calculate the cached value. It has a method ",(0,o.mdx)("inlineCode",{parentName:"p"},"name()")," which is used to identify which cached value the method is calculating. Cached values can only depend upon props and state - any other parameters are not allowed."),(0,o.mdx)("pre",null,(0,o.mdx)("code",{parentName:"pre",className:"language-java"},'@OnCreateLayout\nstatic Component onCreateLayout(\n    ComponentContext c,\n    @Prop Object prop,\n    @CachedValue int expensiveValue) {\n  return getComponent(prop, expensiveValue);\n}\n\n@OnCalculateCachedValue(name = "expensiveValue")\nstatic int onCalculateExpensiveValue(\n    @Prop Object prop,\n    @State Object state) {\n  return doExpensiveCalculation(prop, state);\n}\n')),(0,o.mdx)("p",null,(0,o.mdx)("inlineCode",{parentName:"p"},"@OnCalculateCachedValue")," is called whenever the dependent props or state change - i.e. an equality check on them fails."),(0,o.mdx)("p",null,"Cached values are stored on the ",(0,o.mdx)("inlineCode",{parentName:"p"},"ComponentTree"),", so they will live for the lifetime of the ",(0,o.mdx)("inlineCode",{parentName:"p"},"ComponentTree"),"."))}h.isMDXComponent=!0}}]);