"use strict";(self.webpackChunk=self.webpackChunk||[]).push([[9241],{3905:(e,n,t)=>{t.r(n),t.d(n,{MDXContext:()=>u,MDXProvider:()=>c,mdx:()=>g,useMDXComponents:()=>d,withMDXComponents:()=>m});var o=t(67294);function i(e,n,t){return n in e?Object.defineProperty(e,n,{value:t,enumerable:!0,configurable:!0,writable:!0}):e[n]=t,e}function a(){return a=Object.assign||function(e){for(var n=1;n<arguments.length;n++){var t=arguments[n];for(var o in t)Object.prototype.hasOwnProperty.call(t,o)&&(e[o]=t[o])}return e},a.apply(this,arguments)}function s(e,n){var t=Object.keys(e);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);n&&(o=o.filter((function(n){return Object.getOwnPropertyDescriptor(e,n).enumerable}))),t.push.apply(t,o)}return t}function r(e){for(var n=1;n<arguments.length;n++){var t=null!=arguments[n]?arguments[n]:{};n%2?s(Object(t),!0).forEach((function(n){i(e,n,t[n])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(t)):s(Object(t)).forEach((function(n){Object.defineProperty(e,n,Object.getOwnPropertyDescriptor(t,n))}))}return e}function l(e,n){if(null==e)return{};var t,o,i=function(e,n){if(null==e)return{};var t,o,i={},a=Object.keys(e);for(o=0;o<a.length;o++)t=a[o],n.indexOf(t)>=0||(i[t]=e[t]);return i}(e,n);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(o=0;o<a.length;o++)t=a[o],n.indexOf(t)>=0||Object.prototype.propertyIsEnumerable.call(e,t)&&(i[t]=e[t])}return i}var u=o.createContext({}),m=function(e){return function(n){var t=d(n.components);return o.createElement(e,a({},n,{components:t}))}},d=function(e){var n=o.useContext(u),t=n;return e&&(t="function"==typeof e?e(n):r(r({},n),e)),t},c=function(e){var n=d(e.components);return o.createElement(u.Provider,{value:n},e.children)},h="mdxType",p={inlineCode:"code",wrapper:function(e){var n=e.children;return o.createElement(o.Fragment,{},n)}},f=o.forwardRef((function(e,n){var t=e.components,i=e.mdxType,a=e.originalType,s=e.parentName,u=l(e,["components","mdxType","originalType","parentName"]),m=d(t),c=i,h=m["".concat(s,".").concat(c)]||m[c]||p[c]||a;return t?o.createElement(h,r(r({ref:n},u),{},{components:t})):o.createElement(h,r({ref:n},u))}));function g(e,n){var t=arguments,i=n&&n.mdxType;if("string"==typeof e||i){var a=t.length,s=new Array(a);s[0]=f;var r={};for(var l in n)hasOwnProperty.call(n,l)&&(r[l]=n[l]);r.originalType=e,r[h]="string"==typeof e?e:i,s[1]=r;for(var u=2;u<a;u++)s[u]=t[u];return o.createElement.apply(null,s)}return o.createElement.apply(null,t)}f.displayName="MDXCreateElement"},76812:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>m,contentTitle:()=>l,default:()=>p,frontMatter:()=>r,metadata:()=>u,toc:()=>d});var o=t(87462),i=t(63366),a=(t(67294),t(3905)),s=["components"],r={id:"mount-extensions",title:"Mount Extensions"},l=void 0,u={unversionedId:"mount-extensions",id:"mount-extensions",title:"Mount Extensions",description:"Mount Extensions are a way of modularising Litho\u2019s default behaviour, as it\u2019s being changed from a monolithic framework to a modular framework that\u2019s split into the rendering engine (RenderCore) and extensions for custom behaviour.",source:"@site/../docs/mountextensions.md",sourceDirName:".",slug:"/mount-extensions",permalink:"/docs/mount-extensions",draft:!1,editUrl:"https://github.com/facebook/litho/edit/master/website/../docs/mountextensions.md",tags:[],version:"current",frontMatter:{id:"mount-extensions",title:"Mount Extensions"}},m={},d=[{value:"MountExtension",id:"mountextension",level:2},{value:"Acquiring mount references",id:"acquiring-mount-references",level:3},{value:"MountDelegate",id:"mountdelegate",level:2},{value:"MountDelegateTarget",id:"mountdelegatetarget",level:2},{value:"Handling new layout results",id:"handling-new-layout-results",level:2},{value:"Handling position in viewport changes",id:"handling-position-in-viewport-changes",level:2},{value:"Example: custom visibility event processing",id:"example-custom-visibility-event-processing",level:2}],c={toc:d},h="wrapper";function p(e){var n=e.components,t=(0,i.Z)(e,s);return(0,a.mdx)(h,(0,o.Z)({},c,t,{components:n,mdxType:"MDXLayout"}),(0,a.mdx)("p",null,"Mount Extensions are a way of modularising Litho\u2019s default behaviour, as it\u2019s being changed from a monolithic framework to a modular framework that\u2019s split into the rendering engine (RenderCore) and extensions for custom behaviour.\nMount Extensions are plug-in classes which can be enabled on a LithoView to add custom behaviour that RenderCore doesn\u2019t implement. Mount Extensions can alter mount behaviour or can process information at mount time."),(0,a.mdx)("p",null,"The RenderCore and Mount Extensions implementation of Litho is currently under the test and some features may not work as expected."),(0,a.mdx)("p",null,"Litho provides features which are built into the the core of the framework, without the possibility of turning them off or customising behaviour. Such features include animations, incremental mount or dispatching visibility events. With Mount Extensions, the implementation of these features is extracted out of the core framework into separate extensions which work independently:"),(0,a.mdx)("ul",null,(0,a.mdx)("li",{parentName:"ul"},"IncrementalMountExtension"),(0,a.mdx)("li",{parentName:"ul"},"TransitionsExtension"),(0,a.mdx)("li",{parentName:"ul"},"VisibilityExtension")),(0,a.mdx)("p",null,"There are three main concepts related to the MountExtensions API:"),(0,a.mdx)("h2",{id:"mountextension"},"MountExtension"),(0,a.mdx)("p",null,"MountExtension: a class for customising mount behaviour. By default, all mount items are mounted if their host (the LithoView) is intersecting the viewport."),(0,a.mdx)("h3",{id:"acquiring-mount-references"},"Acquiring mount references"),(0,a.mdx)("p",null,"A Mount Extension can alter that behaviour by acquiring or releasing a mount reference for an item. Acquiring a mount reference means that the extension wants the item to be mounted. When the extension is no longer interested in keeping the item mounted, it can release the reference it previously acquired.\nFor example, the IncrementalMountExtension will only acquire a mount reference for items which are themselves visible in the viewport and will not acquire for items that are off-screen even if their host is visible.\nIndependently, the Animations Extension could acquire mount reference for items that are not visible on screen if their parent host is animating.\nAn extension can only release mount references for items it previously acquired and it has no information about whether other extensions have acquired an item."),(0,a.mdx)("p",null,"Let\u2019s take a look at the MountExtension API:"),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-java"},"public class MountExtension<Input> {\n\n   public void registerToDelegate(MountDelegate mountDelegate) {\n   }\n\n    /**\n   * Called for setting up input on the extension before mounting.\n   *\n   * @param input The new input the extension should use.\n   */\n  public void beforeMount(Input input, @Nullable Rect localVisibleRect) {}\n\n  /** Called after mounting is finished. */\n  public void afterMount() {}\n\n  /** Called when the visible bounds of the Host change. */\n  public void onVisibleBoundsChanged(@Nullable Rect localVisibleRect) {}\n\n  /** Called after all the Host's children have been unmounted. */\n  public void onUnmount() {}\n\n  /** Called after all the Host's children have been unbound. */\n  public void onUnbind() {}\n\n  /** Called to request that this item should be mounted.\n   * If isMounting is true, it will be immediately mounted if it's not mounted already.\n   * Otherwise, it will be mounted on the next mount pass.\n  */\n  public void acquireMountRef(long nodeId, int index, boolean isMounting) {}\n\n  /**\n   * Called to release a mount referece and indicate that this extension\n   * does not need the item to be mounted anymore.\n   * If isMounting is true and the item's mount reference is no longer acquired by\n   * any extension, it will be immediately unmounted.\n   */\n  public void releaseMountRef(long nodeId, int i, boolean isMounting) {\n}\n")),(0,a.mdx)("p",null,"The Input is a type that represents the data for the Mount Extension to process. For example, in the case of VisibilityOutputsExtension, the Input implementation provides information about all the bounds and visibility event handlers for the Components."),(0,a.mdx)("h2",{id:"mountdelegate"},"MountDelegate"),(0,a.mdx)("p",null,"A MountDelegate is a convenience class to manage all the extensions associated with a particular host. The MountDelegate has the list of all the enabled extensions which can alter mount behaviour. It aggregates the results of acquiring and releasing mount references by all the extensions and it notifies the MountDelegateTarget when an item needs to be mounted or unmounted.\nThe MountDelegate sums up the acquired mount references for all the items. It increases the total count when one of the extensions calls ",(0,a.mdx)("inlineCode",{parentName:"p"},"acquireMountRef")," and it decreases the total when ",(0,a.mdx)("inlineCode",{parentName:"p"},"releaseMountRef")," is called. When the total is positive, meaning at least one extension needs to mount an item, the MountDelegateTarget is notified and the item is mounted. If the total count reaches 0, the item no longer needs to be mounted by any extension so it will be unmounted."),(0,a.mdx)("h2",{id:"mountdelegatetarget"},"MountDelegateTarget"),(0,a.mdx)("p",null,"The MountDelegateTarget is a class that\u2019s capable of creating, mounting and unmounting mount items. In Litho, the MountDelegateTarget implementation is the MountState. The MountDelegateTarget has a MountDelegate reference which it can query to check an item\u2019s mount reference count and decide whether it should be mounted, unmounted or updated.\nThe MountDelegateTarget is also notified if an extension requires an item to be mounted or unmounted immediately. A MountExtension can influence what is mounted, but the MountDelegateTarget performs the mount operation."),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-java"},"public interface MountDelegateTarget {\n\n  /**\n  * Can be called by a registered extension to request mount for a node.\n  */\n  void notifyMount(final int position);\n\n /**\n  * Can be called by a registered extension to request unmount for a node.\n  */\n  void notifyUnmount(int position);\n\n\n  /**\n  * Is called by a host to request mount when a new layout tree is available.\n  */\n  void mount(RenderTree renderTree);\n\n  void registerMountDelegateExtension(MountExtension mountExtension);\n\n  // Check javadocs for the full API\n}\n")),(0,a.mdx)("h2",{id:"handling-new-layout-results"},"Handling new layout results"),(0,a.mdx)("p",null,"The host which is responsible for calling ",(0,a.mdx)("inlineCode",{parentName:"p"},"MountState.mount()")," (in our case, the LithoView) will call the appropriate callbacks on the list of enabled extensions."),(0,a.mdx)("p",null,"For example, when a new layout for the LithoView\u2019s ComponentTree is calculated, LithoView needs to mount the content for the new layout and it will perform the following sequence:"),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-java"},"private void mount(LayoutState layoutState, @Nullable Rect currentVisibleArea) {\n  for (MountExtension mountExtension: enabledExtensions) {\n    extension.beforeMount(layoutState, currentVisibleArea);\n   }\n\n  mountDelegateTarget.mount(layoutState.toRenderTree());\n\n  for (MountExtension mountExtension: enabledExtensions) {\n    extension.afterMount();\n  }\n}\n\n")),(0,a.mdx)("h2",{id:"handling-position-in-viewport-changes"},"Handling position in viewport changes"),(0,a.mdx)("p",null,"Some Mount Extensions also need to process information when the host only changes visible bounds, even if no new layout result needs to be mounted. For example, the ",(0,a.mdx)("inlineCode",{parentName:"p"},"VisibilityOutputsExtension")," needs to listen to the host\u2019s visible bounds changing on every scrolling frame and check if any items changed visibility status to dispatch events.\nIn that case, the host performs the following sequence:"),(0,a.mdx)("pre",null,(0,a.mdx)("code",{parentName:"pre",className:"language-java"},"for (MountExtension mountExtension: enabledExtensions) {\n  mountExtension.onVisibleBoundsChanged(currentVisibleArea);\n}\n")),(0,a.mdx)("p",null,"Since the layout result has not changed in this case, we don\u2019t need to send a new input to process - it was already provided by calling ",(0,a.mdx)("inlineCode",{parentName:"p"},"beforeMount"),"."),(0,a.mdx)("h2",{id:"example-custom-visibility-event-processing"},"Example: custom visibility event processing"),(0,a.mdx)("p",null,"At the moment, the extensions implementation and the usage of a MountDelegateTarget in Litho are still being tested and is unstable - there\u2019s no API yet to provide a custom extension. Until the extension implementations and the integration between the LithoView and the MountDelegateTarget have been stabilised, we don\u2019t recommend using this and we won\u2019t provide an API to enable custom extensions on the LithoView."),(0,a.mdx)("p",null,"The current implementation of MountState is a hybrid which uses the VisibilityOutputsExtension for visibility events processing during mount, but no MountDelegateTarget is being used. The extension\u2019s callbacks are called manually by MountState - this is not the end state we want, but it\u2019s an incremental step towards using extensions for all mount-time capabilities.\nFor this hybrid state, we can expose a test API to swap out the default implementation of the VisibilityOutputsExtension with a custom implementation. For example, if you want to receive visibility events even if your items are not visible on screen, but their host is visible, you can implement that behaviour in a custom visibility extension and pass that to the LithoView to use and override the default visibility behaviour."),(0,a.mdx)("p",null,"Demo: ",(0,a.mdx)("a",{parentName:"p",href:"https://github.com/facebook/litho/pull/714"},"https://github.com/facebook/litho/pull/714")))}p.isMDXComponent=!0}}]);