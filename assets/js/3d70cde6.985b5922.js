"use strict";(self.webpackChunk=self.webpackChunk||[]).push([[5529],{3905:(e,t,n)=>{n.r(t),n.d(t,{MDXContext:()=>c,MDXProvider:()=>p,mdx:()=>g,useMDXComponents:()=>u,withMDXComponents:()=>d});var o=n(67294);function r(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function a(){return a=Object.assign||function(e){for(var t=1;t<arguments.length;t++){var n=arguments[t];for(var o in n)Object.prototype.hasOwnProperty.call(n,o)&&(e[o]=n[o])}return e},a.apply(this,arguments)}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);t&&(o=o.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,o)}return n}function l(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){r(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function s(e,t){if(null==e)return{};var n,o,r=function(e,t){if(null==e)return{};var n,o,r={},a=Object.keys(e);for(o=0;o<a.length;o++)n=a[o],t.indexOf(n)>=0||(r[n]=e[n]);return r}(e,t);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(o=0;o<a.length;o++)n=a[o],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(r[n]=e[n])}return r}var c=o.createContext({}),d=function(e){return function(t){var n=u(t.components);return o.createElement(e,a({},t,{components:n}))}},u=function(e){var t=o.useContext(c),n=t;return e&&(n="function"==typeof e?e(t):l(l({},t),e)),n},p=function(e){var t=u(e.components);return o.createElement(c.Provider,{value:t},e.children)},m="mdxType",f={inlineCode:"code",wrapper:function(e){var t=e.children;return o.createElement(o.Fragment,{},t)}},h=o.forwardRef((function(e,t){var n=e.components,r=e.mdxType,a=e.originalType,i=e.parentName,c=s(e,["components","mdxType","originalType","parentName"]),d=u(n),p=r,m=d["".concat(i,".").concat(p)]||d[p]||f[p]||a;return n?o.createElement(m,l(l({ref:t},c),{},{components:n})):o.createElement(m,l({ref:t},c))}));function g(e,t){var n=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var a=n.length,i=new Array(a);i[0]=h;var l={};for(var s in t)hasOwnProperty.call(t,s)&&(l[s]=t[s]);l.originalType=e,l[m]="string"==typeof e?e:r,i[1]=l;for(var c=2;c<a;c++)i[c]=n[c];return o.createElement.apply(null,i)}return o.createElement.apply(null,n)}h.displayName="MDXCreateElement"},90369:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>d,contentTitle:()=>s,default:()=>f,frontMatter:()=>l,metadata:()=>c,toc:()=>u});var o=n(87462),r=n(63366),a=(n(67294),n(3905)),i=["components"],l={id:"faq",title:"FAQ"},s=void 0,c={unversionedId:"faq",id:"faq",title:"FAQ",description:"Frequently Asked Questions",source:"@site/../docs/faq.md",sourceDirName:".",slug:"/faq",permalink:"/docs/faq",draft:!1,editUrl:"https://github.com/facebook/litho/edit/master/website/../docs/faq.md",tags:[],version:"current",frontMatter:{id:"faq",title:"FAQ"}},d={},u=[{value:"Frequently Asked Questions",id:"frequently-asked-questions",level:2},{value:"Using Litho with React Native",id:"using-litho-with-react-native",level:3},{value:"Forcing newer versions of the Support Library",id:"forcing-newer-versions-of-the-support-library",level:3},{value:"Could not initialize class com.facebook.yoga.YogaNode",id:"could-not-initialize-class-comfacebookyogayoganode",level:3},{value:"<code>@InjectProp</code> fails for generated components",id:"injectprop-fails-for-generated-components",level:3}],p={toc:u},m="wrapper";function f(e){var t=e.components,n=(0,r.Z)(e,i);return(0,a.mdx)(m,(0,o.Z)({},p,n,{components:t,mdxType:"MDXLayout"}),(0,a.mdx)("h2",{id:"frequently-asked-questions"},"Frequently Asked Questions"),(0,a.mdx)("h3",{id:"using-litho-with-react-native"},"Using Litho with React Native"),(0,a.mdx)("p",null,"React Native ships with its own version of Yoga which can cause conflicts when merging the\ndex files. In order to avoid this, you can instruct Gradle to exclude one of the Yoga modules."),(0,a.mdx)("p",null,"To do this, add a section like this to your Gradle file after the dependency declaration:"),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-gradle"},"configurations.all {\n  exclude group: 'com.facebook.yoga', module: 'yoga'\n  exclude group: 'com.facebook.infer.annotation', module: 'infer-annotation'\n  exclude group: 'com.google.code.findbugs', module: 'jsr305'\n}\n")),(0,a.mdx)("p",null,"For more information, check out ",(0,a.mdx)("a",{parentName:"p",href:"https://github.com/facebook/litho/issues/224"},"issue #224"),"."),(0,a.mdx)("h3",{id:"forcing-newer-versions-of-the-support-library"},"Forcing newer versions of the Support Library"),(0,a.mdx)("p",null,"If you want to override the version of the support library Litho requires, you can set\nthe overrides in your ",(0,a.mdx)("inlineCode",{parentName:"p"},"build.gradle"),":"),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-gradle"},"configurations.all {\n  resolutionStrategy {\n    force 'com.android.support:appcompat-v7:26.+'\n    force 'com.android.support:support-compat:26.+'\n    force 'com.android.support:support-core-ui:26.+'\n    force 'com.android.support:support-annotations:26.+'\n    force 'com.android.support:recyclerview-v7:26.+'\n  }\n}\n")),(0,a.mdx)("h3",{id:"could-not-initialize-class-comfacebookyogayoganode"},"Could not initialize class com.facebook.yoga.YogaNode"),(0,a.mdx)("p",null,"If you are getting this error when running a Litho unit test, go through these steps:"),(0,a.mdx)("ul",null,(0,a.mdx)("li",{parentName:"ul"},"Ensure Java 8 is correctly set up. If you are on a Mac, make sure that ",(0,a.mdx)("inlineCode",{parentName:"li"},"which java"),"\npoints to something like ",(0,a.mdx)("inlineCode",{parentName:"li"},"/Library/Java/JavaVirtualMachines/jdk1.8.0_111.jdk/Contents/Home/bin/java"),"\nand ",(0,a.mdx)("em",{parentName:"li"},"not")," ",(0,a.mdx)("inlineCode",{parentName:"li"},"/usr/bin/java"),". Otherwise, update your ",(0,a.mdx)("inlineCode",{parentName:"li"},"$PATH")," accordingly.")),(0,a.mdx)("p",null,(0,a.mdx)("strong",{parentName:"p"},"For Buck")),(0,a.mdx)("ul",null,(0,a.mdx)("li",{parentName:"ul"},"Make sure your tests use the ",(0,a.mdx)("inlineCode",{parentName:"li"},"litho_robolectric4_test")," which sets up the necessary dependencies on the native libraries."),(0,a.mdx)("li",{parentName:"ul"},"If your tests use PowerMock, use the ",(0,a.mdx)("inlineCode",{parentName:"li"},"litho_robolectric4_powermock_test")," or set the ",(0,a.mdx)("inlineCode",{parentName:"li"},"fork_mode")," manually to ",(0,a.mdx)("inlineCode",{parentName:"li"},"per_test")," which\nensures that class loaders aren't reused across threads."),(0,a.mdx)("li",{parentName:"ul"},"Try ",(0,a.mdx)("inlineCode",{parentName:"li"},"buck kill")," and ",(0,a.mdx)("inlineCode",{parentName:"li"},"buck clean"),"."),(0,a.mdx)("li",{parentName:"ul"},"If everything else fails, reboot.")),(0,a.mdx)("p",null,(0,a.mdx)("strong",{parentName:"p"},"For Gradle")),(0,a.mdx)("ul",null,(0,a.mdx)("li",{parentName:"ul"},"Follow the instructions under ",(0,a.mdx)("a",{parentName:"li",href:"/docs/testing/unit-testing#caveats"},"Unit Testing - Caveats")," for your setup."),(0,a.mdx)("li",{parentName:"ul"},"Relaunch the gradle daemon with ",(0,a.mdx)("inlineCode",{parentName:"li"},"./gradlew --stop"),".")),(0,a.mdx)("h3",{id:"injectprop-fails-for-generated-components"},(0,a.mdx)("inlineCode",{parentName:"h3"},"@InjectProp")," fails for generated components"),(0,a.mdx)("p",null,"When using parallel build systems like Buck, it can be difficult for the build\nsystem to determine the correct order to generate sources in. This can lead to\nessential type information being unavailable, making it impossible to determine\nthe fully qualified name. If a component A tries to use ",(0,a.mdx)("inlineCode",{parentName:"p"},"@InjectProp")," for\nanother generated component B, this can fail if B is part of the same\ncompilation unit, but sits in a different package."),(0,a.mdx)("p",null,"The easiest workaround for this is to help the compiler by moving\neither the referencing or the referenced component into a separate build module.\nSplitting build modules by package is considered a good practice with Buck."))}f.isMDXComponent=!0}}]);