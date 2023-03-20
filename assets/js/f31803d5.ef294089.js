"use strict";(self.webpackChunk=self.webpackChunk||[]).push([[8380],{3905:(e,t,n)=>{n.r(t),n.d(t,{MDXContext:()=>m,MDXProvider:()=>d,mdx:()=>g,useMDXComponents:()=>c,withMDXComponents:()=>u});var a=n(67294);function i(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function r(){return r=Object.assign||function(e){for(var t=1;t<arguments.length;t++){var n=arguments[t];for(var a in n)Object.prototype.hasOwnProperty.call(n,a)&&(e[a]=n[a])}return e},r.apply(this,arguments)}function o(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);t&&(a=a.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,a)}return n}function s(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?o(Object(n),!0).forEach((function(t){i(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):o(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function l(e,t){if(null==e)return{};var n,a,i=function(e,t){if(null==e)return{};var n,a,i={},r=Object.keys(e);for(a=0;a<r.length;a++)n=r[a],t.indexOf(n)>=0||(i[n]=e[n]);return i}(e,t);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);for(a=0;a<r.length;a++)n=r[a],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(i[n]=e[n])}return i}var m=a.createContext({}),u=function(e){return function(t){var n=c(t.components);return a.createElement(e,r({},t,{components:n}))}},c=function(e){var t=a.useContext(m),n=t;return e&&(n="function"==typeof e?e(t):s(s({},t),e)),n},d=function(e){var t=c(e.components);return a.createElement(m.Provider,{value:t},e.children)},p="mdxType",h={inlineCode:"code",wrapper:function(e){var t=e.children;return a.createElement(a.Fragment,{},t)}},f=a.forwardRef((function(e,t){var n=e.components,i=e.mdxType,r=e.originalType,o=e.parentName,m=l(e,["components","mdxType","originalType","parentName"]),u=c(n),d=i,p=u["".concat(o,".").concat(d)]||u[d]||h[d]||r;return n?a.createElement(p,s(s({ref:t},m),{},{components:n})):a.createElement(p,s({ref:t},m))}));function g(e,t){var n=arguments,i=t&&t.mdxType;if("string"==typeof e||i){var r=n.length,o=new Array(r);o[0]=f;var s={};for(var l in t)hasOwnProperty.call(t,l)&&(s[l]=t[l]);s.originalType=e,s[p]="string"==typeof e?e:i,o[1]=s;for(var m=2;m<r;m++)o[m]=n[m];return a.createElement.apply(null,o)}return a.createElement.apply(null,n)}f.displayName="MDXCreateElement"},41086:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>u,contentTitle:()=>l,default:()=>h,frontMatter:()=>s,metadata:()=>m,toc:()=>c});var a=n(87462),i=n(63366),r=(n(67294),n(3905)),o=["components"],s={id:"mountable-measuring",title:"Measuring"},l=void 0,m={unversionedId:"mainconcepts/mountablecomponents/mountable-measuring",id:"mainconcepts/mountablecomponents/mountable-measuring",title:"Measuring",description:"SimpleMountable should implement a measure() function to define how it should measure itself, given width and height specs.",source:"@site/../docs/mainconcepts/mountablecomponents/mountable-measuring.mdx",sourceDirName:"mainconcepts/mountablecomponents",slug:"/mainconcepts/mountablecomponents/mountable-measuring",permalink:"/docs/mainconcepts/mountablecomponents/mountable-measuring",draft:!1,editUrl:"https://github.com/facebook/litho/edit/master/website/../docs/mainconcepts/mountablecomponents/mountable-measuring.mdx",tags:[],version:"current",frontMatter:{id:"mountable-measuring",title:"Measuring"},sidebar:"mainSidebar",previous:{title:"Lifecycle of a SimpleMountable",permalink:"/docs/mainconcepts/mountablecomponents/mountable"},next:{title:"Controllers Pattern",permalink:"/docs/mainconcepts/mountablecomponents/mountable-controllers"}},u={},c=[{value:"Measuring in practice",id:"measuring-in-practice",level:2},{value:"Mathematical calculations on View.MeasureSpec values",id:"mathematical-calculations-on-viewmeasurespec-values",level:2},{value:"Creating a View, measuring it, and reading the measured sizes",id:"creating-a-view-measuring-it-and-reading-the-measured-sizes",level:2}],d={toc:c},p="wrapper";function h(e){var t=e.components,n=(0,i.Z)(e,o);return(0,r.mdx)(p,(0,a.Z)({},d,n,{components:t,mdxType:"MDXLayout"}),(0,r.mdx)("p",null,"SimpleMountable should implement a ",(0,r.mdx)("inlineCode",{parentName:"p"},"measure()")," function to define how it should measure itself, given width and height specs."),(0,r.mdx)("p",null,"The ",(0,r.mdx)("inlineCode",{parentName:"p"},"measure()")," function returns a ",(0,r.mdx)("inlineCode",{parentName:"p"},"MeasureResult")," object that contains the width and height of the content and, optionally, any layout data."),(0,r.mdx)("p",null,"Access to the utility methods that can be used to return appropriate ",(0,r.mdx)("inlineCode",{parentName:"p"},"MeasureResult"),", as well as androidContext and previousLayoutData, is possible in the ",(0,r.mdx)("inlineCode",{parentName:"p"},"measure()")," method via ",(0,r.mdx)("a",{parentName:"p",href:"https://fburl.com/measurescope"},"MeasureScope"),"."),(0,r.mdx)("admonition",{type:"note"},(0,r.mdx)("p",{parentName:"admonition"},(0,r.mdx)("inlineCode",{parentName:"p"},"measure()")," method can be called on any thread, and has the following characteristics:"),(0,r.mdx)("ul",{parentName:"admonition"},(0,r.mdx)("li",{parentName:"ul"},"it must not cause any side effects"),(0,r.mdx)("li",{parentName:"ul"},"it is guaranteed to be called at least once, and can be called multiple times (if ",(0,r.mdx)("inlineCode",{parentName:"li"},"measure()")," is called again in the same layout pass, ",(0,r.mdx)("inlineCode",{parentName:"li"},"previousLayoutData")," parameter will contain the layout data returned by the previous ",(0,r.mdx)("inlineCode",{parentName:"li"},"measure()")," call)"))),(0,r.mdx)("h2",{id:"measuring-in-practice"},"Measuring in practice"),(0,r.mdx)("p",null,"In principle, there are two different ways that measuring can be implemented:"),(0,r.mdx)("h2",{id:"mathematical-calculations-on-viewmeasurespec-values"},"Mathematical calculations on View.MeasureSpec values"),(0,r.mdx)("p",null,"The ",(0,r.mdx)("inlineCode",{parentName:"p"},"widthSpec")," and ",(0,r.mdx)("inlineCode",{parentName:"p"},"heightSpec")," parameters of the ",(0,r.mdx)("inlineCode",{parentName:"p"},"measure()")," method can be used to carry out mathematical calculations and count the final measurements based on ",(0,r.mdx)("inlineCode",{parentName:"p"},"widthMode")," and ",(0,r.mdx)("inlineCode",{parentName:"p"},"heightMode")," that can be retrieved from ",(0,r.mdx)("a",{parentName:"p",href:"https://developer.android.com/reference/android/view/View.MeasureSpec"},"View.MeasureSpec"),", as shown in the following example code:"),(0,r.mdx)("pre",null,(0,r.mdx)("code",{parentName:"pre",className:"language-kotlin",metastring:"file=sample/src/main/java/com/facebook/samples/litho/kotlin/mountables/bindto/ImageViewComponent.kt start=measure_example_start end=measure_example_end",file:"sample/src/main/java/com/facebook/samples/litho/kotlin/mountables/bindto/ImageViewComponent.kt",start:"measure_example_start",end:"measure_example_end"},"override fun MeasureScope.measure(widthSpec: Int, heightSpec: Int): MeasureResult {\n  // if the sizes are unspecified, set default size; otherwise enforce equal sizes\n  return if (SizeSpec.getMode(widthSpec) == SizeSpec.UNSPECIFIED &&\n      SizeSpec.getMode(heightSpec) == SizeSpec.UNSPECIFIED) {\n    MeasureResult(defaultSize, defaultSize)\n  } else {\n    withEqualSize(widthSpec, heightSpec)\n  }\n}\n")),(0,r.mdx)("h2",{id:"creating-a-view-measuring-it-and-reading-the-measured-sizes"},"Creating a View, measuring it, and reading the measured sizes"),(0,r.mdx)("p",null,"Alternatively, a View can be created that represents the mount content then call the ",(0,r.mdx)("inlineCode",{parentName:"p"},"View.measure()")," method on it directly. After ",(0,r.mdx)("inlineCode",{parentName:"p"},"View.measure()")," completes, the measured width and height can be retrieved by calling ",(0,r.mdx)("inlineCode",{parentName:"p"},"View.getMeasuredWidth()")," and ",(0,r.mdx)("inlineCode",{parentName:"p"},"View.getMeasuredHeight()"),":"),(0,r.mdx)("pre",null,(0,r.mdx)("code",{parentName:"pre",className:"language-kotlin",metastring:"file=sample/src/main/java/com/facebook/samples/litho/kotlin/mountables/SampleTextInput.kt start=start_measure_with_view_measurement end=end_measure_with_view_measurement",file:"sample/src/main/java/com/facebook/samples/litho/kotlin/mountables/SampleTextInput.kt",start:"start_measure_with_view_measurement",end:"end_measure_with_view_measurement"},"override fun MeasureScope.measure(widthSpec: Int, heightSpec: Int): MeasureResult {\n  // The height should be the measured height of EditText with relevant params\n  val editTextForMeasure: EditText = AppCompatEditText(androidContext)\n\n  editTextForMeasure.hint = hint\n  editTextForMeasure.background =\n      getBackgroundOrDefault(\n          androidContext,\n          if (inputBackground === ColorDrawable(Color.TRANSPARENT)) editTextForMeasure.background\n          else inputBackground)\n  editTextForMeasure.setText(initialText)\n\n  editTextForMeasure.measure(\n      MeasureUtils.getViewMeasureSpec(widthSpec), MeasureUtils.getViewMeasureSpec(heightSpec))\n\n  val size = Size()\n  size.height = editTextForMeasure.measuredHeight\n\n  // For width we always take all available space, or collapse to 0 if unspecified.\n  if (SizeSpec.getMode(widthSpec) == SizeSpec.UNSPECIFIED) {\n    size.width = 0\n  } else {\n    size.width = Math.min(SizeSpec.getSize(widthSpec), editTextForMeasure.measuredWidth)\n  }\n  return MeasureResult(size.width, size.height, null)\n}\n")),(0,r.mdx)("admonition",{type:"note"},(0,r.mdx)("p",{parentName:"admonition"},"When the width and height can be determined using mathematical calculations, it is preferred over creating a View because the calculations are much more lightweight.")))}h.isMDXComponent=!0}}]);