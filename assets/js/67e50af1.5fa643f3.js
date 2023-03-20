"use strict";(self.webpackChunk=self.webpackChunk||[]).push([[1617],{3905:(e,t,n)=>{n.r(t),n.d(t,{MDXContext:()=>p,MDXProvider:()=>u,mdx:()=>f,useMDXComponents:()=>m,withMDXComponents:()=>c});var o=n(67294);function r(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function a(){return a=Object.assign||function(e){for(var t=1;t<arguments.length;t++){var n=arguments[t];for(var o in n)Object.prototype.hasOwnProperty.call(n,o)&&(e[o]=n[o])}return e},a.apply(this,arguments)}function s(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);t&&(o=o.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,o)}return n}function i(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?s(Object(n),!0).forEach((function(t){r(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):s(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function l(e,t){if(null==e)return{};var n,o,r=function(e,t){if(null==e)return{};var n,o,r={},a=Object.keys(e);for(o=0;o<a.length;o++)n=a[o],t.indexOf(n)>=0||(r[n]=e[n]);return r}(e,t);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(o=0;o<a.length;o++)n=a[o],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(r[n]=e[n])}return r}var p=o.createContext({}),c=function(e){return function(t){var n=m(t.components);return o.createElement(e,a({},t,{components:n}))}},m=function(e){var t=o.useContext(p),n=t;return e&&(n="function"==typeof e?e(t):i(i({},t),e)),n},u=function(e){var t=m(e.components);return o.createElement(p.Provider,{value:t},e.children)},d="mdxType",y={inlineCode:"code",wrapper:function(e){var t=e.children;return o.createElement(o.Fragment,{},t)}},g=o.forwardRef((function(e,t){var n=e.components,r=e.mdxType,a=e.originalType,s=e.parentName,p=l(e,["components","mdxType","originalType","parentName"]),c=m(n),u=r,d=c["".concat(s,".").concat(u)]||c[u]||y[u]||a;return n?o.createElement(d,i(i({ref:t},p),{},{components:n})):o.createElement(d,i({ref:t},p))}));function f(e,t){var n=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var a=n.length,s=new Array(a);s[0]=g;var i={};for(var l in t)hasOwnProperty.call(t,l)&&(i[l]=t[l]);i.originalType=e,i[d]="string"==typeof e?e:r,s[1]=i;for(var p=2;p<a;p++)s[p]=n[p];return o.createElement.apply(null,s)}return o.createElement.apply(null,n)}g.displayName="MDXCreateElement"},7772:(e,t,n)=>{n.d(t,{Z:()=>d});var o=n(87462),r=n(67294),a=n(23746),s=n(7694),i=n(13618),l="0.47.0",p="0.48.0-SNAPSHOT",c="0.10.5",m="0.142.0",u=n(86668);const d=function(e){var t=e.language,n=e.code.replace(/{{site.lithoVersion}}/g,l).replace(/{{site.soloaderVersion}}/g,c).replace(/{{site.lithoSnapshotVersion}}/g,p).replace(/{{site.flipperVersion}}/g,m).trim(),d=(0,u.L)().isDarkTheme?i.Z:s.Z;return r.createElement(a.ZP,(0,o.Z)({},a.lG,{code:n,language:t,theme:d}),(function(e){var t=e.className,n=e.style,o=e.tokens,a=e.getLineProps,s=e.getTokenProps;return r.createElement("pre",{className:t,style:n},o.map((function(e,t){return r.createElement("div",a({line:e,key:t}),e.map((function(e,t){return r.createElement("span",s({token:e,key:t}))})))})))}))}},34358:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>l,default:()=>y,frontMatter:()=>i,metadata:()=>p,toc:()=>m});var o=n(87462),r=n(63366),a=(n(67294),n(3905)),s=(n(7772),["components"]),i={id:"components-basics",title:"Components",keywords:["component","component litho","litho component","litho kcomponent"]},l=void 0,p={unversionedId:"mainconcepts/components-basics",id:"mainconcepts/components-basics",title:"Components",description:"What is a component?",source:"@site/../docs/mainconcepts/components-basics.mdx",sourceDirName:"mainconcepts",slug:"/mainconcepts/components-basics",permalink:"/docs/mainconcepts/components-basics",draft:!1,editUrl:"https://github.com/facebook/litho/edit/master/website/../docs/mainconcepts/components-basics.mdx",tags:[],version:"current",frontMatter:{id:"components-basics",title:"Components",keywords:["component","component litho","litho component","litho kcomponent"]},sidebar:"mainSidebar",previous:{title:"Building Lists",permalink:"/docs/tutorial/building-lists"},next:{title:"Types of Props",permalink:"/docs/mainconcepts/props"}},c={},m=[{value:"What is a component?",id:"what-is-a-component",level:2},{value:"Declaring a Component",id:"declaring-a-component",level:2},{value:"Rules for components",id:"rules-for-components",level:3},{value:"Other ways to configure components",id:"other-ways-to-configure-components",level:3}],u={toc:m},d="wrapper";function y(e){var t=e.components,n=(0,r.Z)(e,s);return(0,a.mdx)(d,(0,o.Z)({},u,n,{components:t,mdxType:"MDXLayout"}),(0,a.mdx)("h2",{id:"what-is-a-component"},"What is a component?"),(0,a.mdx)("p",null,(0,a.mdx)("strong",{parentName:"p"},"Components")," are the fundamental building blocks of UIs in Litho. They consist of a render function that can use the component's ",(0,a.mdx)("strong",{parentName:"p"},"props")," and ",(0,a.mdx)("strong",{parentName:"p"},"state")," to configure and return other components. The returned components are further resolved by recursively calling ",(0,a.mdx)("inlineCode",{parentName:"p"},"render")," on them until the process results in ",(0,a.mdx)("strong",{parentName:"p"},"primitives")," like ",(0,a.mdx)("inlineCode",{parentName:"p"},"Text")," or ",(0,a.mdx)("inlineCode",{parentName:"p"},"Image"),", which can be positioned and materialized on the screen."),(0,a.mdx)("p",null,"The following example shows a component that takes a name as a prop and renders a greeting with a ",(0,a.mdx)("inlineCode",{parentName:"p"},"Text")," component."),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-kotlin",metastring:"file=sample/src/main/java/com/facebook/samples/litho/kotlin/documentation/HelloComponent.kt start=start_simple_example end=end_simple_example",file:"sample/src/main/java/com/facebook/samples/litho/kotlin/documentation/HelloComponent.kt",start:"start_simple_example",end:"end_simple_example"},'class HelloComponent(private val name: String) : KComponent() {\n\n  override fun ComponentScope.render(): Component? {\n    return Text(text = "Hello $name!")\n  }\n}\n')),(0,a.mdx)("admonition",{type:"info"},(0,a.mdx)("p",{parentName:"admonition"},(0,a.mdx)("strong",{parentName:"p"},"What's the relationship between a component and a view?")),(0,a.mdx)("p",{parentName:"admonition"},"In practice, a component is just a type and a bag of props and is cheap to instantiate. Instantiating a component does not instantiate a View. At ",(0,a.mdx)("strong",{parentName:"p"},"mount")," time, only primitive components that mount Views or have View properties (such as ",(0,a.mdx)("inlineCode",{parentName:"p"},"viewTag"),") will materialize Views in the final layout.")),(0,a.mdx)("h2",{id:"declaring-a-component"},"Declaring a Component"),(0,a.mdx)("p",null,"A component is declared as a simple sub-class of ",(0,a.mdx)("inlineCode",{parentName:"p"},"KComponent"),", overriding a single function: ",(0,a.mdx)("inlineCode",{parentName:"p"},"render"),". You can declare the ",(0,a.mdx)("strong",{parentName:"p"},"props")," a component takes as standard ",(0,a.mdx)("inlineCode",{parentName:"p"},"val")," properties; as is normal in Kotlin, these may be declared as optional by providing a default value."),(0,a.mdx)("p",null,"To instantiate an existing component, specify the props in the constructor of that component. By convention, named arguments are used for clarity, even if the prop is required:"),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-kotlin"},'Text(text = "Hello $name!")\n')),(0,a.mdx)("p",null,"Note that KComponents are only supported in Kotlin; if you need to declare a component from Java, look at the ",(0,a.mdx)("a",{parentName:"p",href:"/docs/codegen/layout-specs"},"legacy Spec API"),"."),(0,a.mdx)("h3",{id:"rules-for-components"},"Rules for components"),(0,a.mdx)("p",null,"For correctness, Litho components must follow two functional-programming rules:"),(0,a.mdx)("ol",null,(0,a.mdx)("li",{parentName:"ol"},(0,a.mdx)("strong",{parentName:"li"},"Components must be immutable")," - they only support ",(0,a.mdx)("inlineCode",{parentName:"li"},"val")," properties (no ",(0,a.mdx)("inlineCode",{parentName:"li"},"var")," properties!). Components are re-used across threads, so mutability can introduce subtle but aggravating correctness issues."),(0,a.mdx)("li",{parentName:"ol"},(0,a.mdx)("strong",{parentName:"li"},(0,a.mdx)("inlineCode",{parentName:"strong"},"render")," must be a pure function without side-effects")," - if render is called with the same props and state, it should always return the same result without modifying other app state.")),(0,a.mdx)("h3",{id:"other-ways-to-configure-components"},"Other ways to configure components"),(0,a.mdx)("p",null,"Following are some other ways you can affect your component's rendering:"),(0,a.mdx)("ul",null,(0,a.mdx)("li",{parentName:"ul"},(0,a.mdx)("strong",{parentName:"li"},(0,a.mdx)("a",{parentName:"strong",href:"../../mainconcepts/props/#common-props"},"Common Props"))," - uses the ",(0,a.mdx)("inlineCode",{parentName:"li"},"Style")," object to configure built-in behavior on a component, like click handling and background color."),(0,a.mdx)("li",{parentName:"ul"},(0,a.mdx)("strong",{parentName:"li"},(0,a.mdx)("a",{parentName:"strong",href:"../../mainconcepts/props/#tree-props"},"Tree Props"))," - provides a way to give access to contextual objects, like a theme or logging utility, to all components in a sub-tree without having to manually pass them."),(0,a.mdx)("li",{parentName:"ul"},(0,a.mdx)("strong",{parentName:"li"},(0,a.mdx)("a",{parentName:"strong",href:"/docs/mainconcepts/use-state"},"State"))," - allows a component to persist private state across updates to the tree.")))}y.isMDXComponent=!0},23746:(e,t,n)=>{n.d(t,{ZP:()=>y,lG:()=>s});var o=n(87410);const r={plain:{backgroundColor:"#2a2734",color:"#9a86fd"},styles:[{types:["comment","prolog","doctype","cdata","punctuation"],style:{color:"#6c6783"}},{types:["namespace"],style:{opacity:.7}},{types:["tag","operator","number"],style:{color:"#e09142"}},{types:["property","function"],style:{color:"#9a86fd"}},{types:["tag-id","selector","atrule-id"],style:{color:"#eeebff"}},{types:["attr-name"],style:{color:"#c4b9fe"}},{types:["boolean","string","entity","url","attr-value","keyword","control","directive","unit","statement","regex","atrule","placeholder","variable"],style:{color:"#ffcc99"}},{types:["deleted"],style:{textDecorationLine:"line-through"}},{types:["inserted"],style:{textDecorationLine:"underline"}},{types:["italic"],style:{fontStyle:"italic"}},{types:["important","bold"],style:{fontWeight:"bold"}},{types:["important"],style:{color:"#c4b9fe"}}]};var a=n(67294),s={Prism:o.Z,theme:r};function i(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function l(){return l=Object.assign||function(e){for(var t=1;t<arguments.length;t++){var n=arguments[t];for(var o in n)Object.prototype.hasOwnProperty.call(n,o)&&(e[o]=n[o])}return e},l.apply(this,arguments)}var p=/\r\n|\r|\n/,c=function(e){0===e.length?e.push({types:["plain"],content:"\n",empty:!0}):1===e.length&&""===e[0].content&&(e[0].content="\n",e[0].empty=!0)},m=function(e,t){var n=e.length;return n>0&&e[n-1]===t?e:e.concat(t)},u=function(e,t){var n=e.plain,o=Object.create(null),r=e.styles.reduce((function(e,n){var o=n.languages,r=n.style;return o&&!o.includes(t)||n.types.forEach((function(t){var n=l({},e[t],r);e[t]=n})),e}),o);return r.root=n,r.plain=l({},n,{backgroundColor:null}),r};function d(e,t){var n={};for(var o in e)Object.prototype.hasOwnProperty.call(e,o)&&-1===t.indexOf(o)&&(n[o]=e[o]);return n}const y=function(e){function t(){for(var t=this,n=[],o=arguments.length;o--;)n[o]=arguments[o];e.apply(this,n),i(this,"getThemeDict",(function(e){if(void 0!==t.themeDict&&e.theme===t.prevTheme&&e.language===t.prevLanguage)return t.themeDict;t.prevTheme=e.theme,t.prevLanguage=e.language;var n=e.theme?u(e.theme,e.language):void 0;return t.themeDict=n})),i(this,"getLineProps",(function(e){var n=e.key,o=e.className,r=e.style,a=l({},d(e,["key","className","style","line"]),{className:"token-line",style:void 0,key:void 0}),s=t.getThemeDict(t.props);return void 0!==s&&(a.style=s.plain),void 0!==r&&(a.style=void 0!==a.style?l({},a.style,r):r),void 0!==n&&(a.key=n),o&&(a.className+=" "+o),a})),i(this,"getStyleForToken",(function(e){var n=e.types,o=e.empty,r=n.length,a=t.getThemeDict(t.props);if(void 0!==a){if(1===r&&"plain"===n[0])return o?{display:"inline-block"}:void 0;if(1===r&&!o)return a[n[0]];var s=o?{display:"inline-block"}:{},i=n.map((function(e){return a[e]}));return Object.assign.apply(Object,[s].concat(i))}})),i(this,"getTokenProps",(function(e){var n=e.key,o=e.className,r=e.style,a=e.token,s=l({},d(e,["key","className","style","token"]),{className:"token "+a.types.join(" "),children:a.content,style:t.getStyleForToken(a),key:void 0});return void 0!==r&&(s.style=void 0!==s.style?l({},s.style,r):r),void 0!==n&&(s.key=n),o&&(s.className+=" "+o),s})),i(this,"tokenize",(function(e,t,n,o){var r={code:t,grammar:n,language:o,tokens:[]};e.hooks.run("before-tokenize",r);var a=r.tokens=e.tokenize(r.code,r.grammar,r.language);return e.hooks.run("after-tokenize",r),a}))}return e&&(t.__proto__=e),t.prototype=Object.create(e&&e.prototype),t.prototype.constructor=t,t.prototype.render=function(){var e=this.props,t=e.Prism,n=e.language,o=e.code,r=e.children,a=this.getThemeDict(this.props),s=t.languages[n];return r({tokens:function(e){for(var t=[[]],n=[e],o=[0],r=[e.length],a=0,s=0,i=[],l=[i];s>-1;){for(;(a=o[s]++)<r[s];){var u=void 0,d=t[s],y=n[s][a];if("string"==typeof y?(d=s>0?d:["plain"],u=y):(d=m(d,y.type),y.alias&&(d=m(d,y.alias)),u=y.content),"string"==typeof u){var g=u.split(p),f=g.length;i.push({types:d,content:g[0]});for(var h=1;h<f;h++)c(i),l.push(i=[]),i.push({types:d,content:g[h]})}else s++,t.push(d),n.push(u),o.push(0),r.push(u.length)}s--,t.pop(),n.pop(),o.pop(),r.pop()}return c(i),l}(void 0!==s?this.tokenize(t,o,s,n):[o]),className:"prism-code language-"+n,style:void 0!==a?a.root:{},getLineProps:this.getLineProps,getTokenProps:this.getTokenProps})},t}(a.Component)},13618:(e,t,n)=>{n.d(t,{Z:()=>o});const o={plain:{color:"#F8F8F2",backgroundColor:"#282A36"},styles:[{types:["prolog","constant","builtin"],style:{color:"rgb(189, 147, 249)"}},{types:["inserted","function"],style:{color:"rgb(80, 250, 123)"}},{types:["deleted"],style:{color:"rgb(255, 85, 85)"}},{types:["changed"],style:{color:"rgb(255, 184, 108)"}},{types:["punctuation","symbol"],style:{color:"rgb(248, 248, 242)"}},{types:["string","char","tag","selector"],style:{color:"rgb(255, 121, 198)"}},{types:["keyword","variable"],style:{color:"rgb(189, 147, 249)",fontStyle:"italic"}},{types:["comment"],style:{color:"rgb(98, 114, 164)"}},{types:["attr-name"],style:{color:"rgb(241, 250, 140)"}}]}},7694:(e,t,n)=>{n.d(t,{Z:()=>o});const o={plain:{color:"#393A34",backgroundColor:"#f6f8fa"},styles:[{types:["comment","prolog","doctype","cdata"],style:{color:"#999988",fontStyle:"italic"}},{types:["namespace"],style:{opacity:.7}},{types:["string","attr-value"],style:{color:"#e3116c"}},{types:["punctuation","operator"],style:{color:"#393A34"}},{types:["entity","url","symbol","number","boolean","variable","constant","property","regex","inserted"],style:{color:"#36acaa"}},{types:["atrule","keyword","attr-name","selector"],style:{color:"#00a4db"}},{types:["function","deleted","tag"],style:{color:"#d73a49"}},{types:["function-variable"],style:{color:"#6f42c1"}},{types:["tag","selector","keyword"],style:{color:"#00009f"}}]}}}]);