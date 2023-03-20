"use strict";(self.webpackChunk=self.webpackChunk||[]).push([[4960],{3905:(e,t,n)=>{n.r(t),n.d(t,{MDXContext:()=>l,MDXProvider:()=>m,mdx:()=>v,useMDXComponents:()=>u,withMDXComponents:()=>p});var r=n(67294);function a(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function o(){return o=Object.assign||function(e){for(var t=1;t<arguments.length;t++){var n=arguments[t];for(var r in n)Object.prototype.hasOwnProperty.call(n,r)&&(e[r]=n[r])}return e},o.apply(this,arguments)}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function s(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){a(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function c(e,t){if(null==e)return{};var n,r,a=function(e,t){if(null==e)return{};var n,r,a={},o=Object.keys(e);for(r=0;r<o.length;r++)n=o[r],t.indexOf(n)>=0||(a[n]=e[n]);return a}(e,t);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);for(r=0;r<o.length;r++)n=o[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(a[n]=e[n])}return a}var l=r.createContext({}),p=function(e){return function(t){var n=u(t.components);return r.createElement(e,o({},t,{components:n}))}},u=function(e){var t=r.useContext(l),n=t;return e&&(n="function"==typeof e?e(t):s(s({},t),e)),n},m=function(e){var t=u(e.components);return r.createElement(l.Provider,{value:t},e.children)},d="mdxType",h={inlineCode:"code",wrapper:function(e){var t=e.children;return r.createElement(r.Fragment,{},t)}},f=r.forwardRef((function(e,t){var n=e.components,a=e.mdxType,o=e.originalType,i=e.parentName,l=c(e,["components","mdxType","originalType","parentName"]),p=u(n),m=a,d=p["".concat(i,".").concat(m)]||p[m]||h[m]||o;return n?r.createElement(d,s(s({ref:t},l),{},{components:n})):r.createElement(d,s({ref:t},l))}));function v(e,t){var n=arguments,a=t&&t.mdxType;if("string"==typeof e||a){var o=n.length,i=new Array(o);i[0]=f;var s={};for(var c in t)hasOwnProperty.call(t,c)&&(s[c]=t[c]);s.originalType=e,s[d]="string"==typeof e?e:a,i[1]=s;for(var l=2;l<o;l++)i[l]=n[l];return r.createElement.apply(null,i)}return r.createElement.apply(null,n)}f.displayName="MDXCreateElement"},18002:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>p,contentTitle:()=>c,default:()=>h,frontMatter:()=>s,metadata:()=>l,toc:()=>u});var r=n(87462),a=n(63366),o=(n(67294),n(3905)),i=["components"],s={id:"hoisting-state",title:"Hoisting State"},c=void 0,l={unversionedId:"mainconcepts/coordinate-state-actions/hoisting-state",id:"mainconcepts/coordinate-state-actions/hoisting-state",title:"Hoisting State",description:"Often, several components need to reflect the same changing value. Rather than a state for each component, it is better to host a single state in their closest common ancestor.",source:"@site/../docs/mainconcepts/coordinate-state-actions/hoisting-state.md",sourceDirName:"mainconcepts/coordinate-state-actions",slug:"/mainconcepts/coordinate-state-actions/hoisting-state",permalink:"/docs/mainconcepts/coordinate-state-actions/hoisting-state",draft:!1,editUrl:"https://github.com/facebook/litho/edit/master/website/../docs/mainconcepts/coordinate-state-actions/hoisting-state.md",tags:[],version:"current",frontMatter:{id:"hoisting-state",title:"Hoisting State"},sidebar:"mainSidebar",previous:{title:"Props vs. State",permalink:"/docs/best-practices/props-vs-state"},next:{title:"Communicating Between Components",permalink:"/docs/mainconcepts/coordinate-state-actions/communicating-between-components"}},p={},u=[{value:"Interface Scenario",id:"interface-scenario",level:2}],m={toc:u},d="wrapper";function h(e){var t=e.components,n=(0,a.Z)(e,i);return(0,o.mdx)(d,(0,r.Z)({},m,n,{components:t,mdxType:"MDXLayout"}),(0,o.mdx)("p",null,"Often, several components need to reflect the same changing value. Rather than a ",(0,o.mdx)("a",{parentName:"p",href:"/docs/codegen/state-for-specs"},"state")," for each component, it is better to host a single state in their closest common ancestor."),(0,o.mdx)("h2",{id:"interface-scenario"},"Interface Scenario"),(0,o.mdx)("p",null,"Consider an interface that converts temperatures between Celsius and Fahrenheit where modifying one value will cause the other to be updated."),(0,o.mdx)("p",null,"Using separate Celsius and Fahrenheit states would be difficult and error prone: complicated book-keeping would be needed to keep the two values synchronised. Furthermore, these values would represent the same temperature, so there is repetition of data and no single source of truth."),(0,o.mdx)("p",null,"It's preferable to make each temperature input component stateless and to use a single state in the parent. This is called a 'Hoisting State', as the state is lifted up from the children into the parent."),(0,o.mdx)("p",null,"A single state can be introduced in the parent to represent the temperature in Celsius. The latest value and an update callback can be passed down as a prop to the inputs. For the Fahrenheit component, the temperature conversion formula is applied to the prop and in the update callback. Now, all the child components can use and modify the temperature value. The following code provides an example."),(0,o.mdx)("pre",null,(0,o.mdx)("code",{parentName:"pre",className:"language-kotlin",metastring:"file=sample/src/main/java/com/facebook/samples/litho/kotlin/documentation/HoistState.kt  start=start_example end=end_example",file:"sample/src/main/java/com/facebook/samples/litho/kotlin/documentation/HoistState.kt","":!0,start:"start_example",end:"end_example"},"class TemperatureConvertor : KComponent() {\n  override fun ComponentScope.render(): Component {\n    // Singe hoisted state\n    val temperatureCelsius = useState { 0.0 }\n\n    return Column {\n      child(\n          TemperatureInput(\n              value = temperatureCelsius.value,\n              scale = TemperatureScale.Celsius,\n              onReturn = { newTemp -> temperatureCelsius.update(newTemp) }))\n      child(\n          TemperatureInput(\n              value = toFahrenheit(temperatureCelsius.value),\n              scale = TemperatureScale.Fahrenheit,\n              onReturn = { newTemp -> temperatureCelsius.update(toCelsius(newTemp)) }))\n    }\n  }\n}\n")))}h.isMDXComponent=!0}}]);