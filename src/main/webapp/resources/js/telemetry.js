(function(h,l,n,p){function g(b,c){return(b||"").length>c?b.substr(0,c-3)+"...":b}function m(b,c,a,d){var f=e.track.timing;b.requestStart&&(f(c,"DNS",b.domainLookupEnd-b.domainLookupStart+.5|0,a,d),f(c,"TCP",(b.secureConnectionStart?b.secureConnectionStart:b.connectEnd)-b.connectStart+.5|0,a,d),f(c,"TTFB",b.responseStart-(b.startTime||b.navigationStart)+.5|0,a,d));b.secureConnectionStart&&f(c,"SSL",b.connectEnd-b.secureConnectionStart+.5|0,a,d)}var e=h[l]=h[l]||{};e.extend=function(){var b=arguments[0],
c=[].slice.call(arguments,1),a,d;for(a=0;a<c.length;++a)if(c[a])for(d in c[a])c[a].hasOwnProperty(d)&&void 0!=c[a][d]&&""!==c[a][d]&&(b[d]=c[a][d]);return b};e.track=function(b,c){if(1!=navigator.doNotTrack){var a=e.extend({},e.track.defaultParams,b,c),d=void 0,f=void 0,d=[];for(f in a)a.hasOwnProperty(f)&&void 0!=a[f]&&""!==a[f]&&d.push(encodeURIComponent(f)+"\x3d"+encodeURIComponent(a[f]));a="https://www.google-analytics.com/collect?"+d.join("\x26");try{navigator.sendBeacon(a)}catch(g){(new Image).src=
a}}};e.track.defaultParams={v:1,tid:n,dh:p,cid:+new Date,aip:1,ul:navigator.userLanguage||navigator.language};e.track.timingSamplingRate=.2;e.track.pageview=function(b,c,a){e.track({t:"pageview",dl:g(b,2048),dt:g(c,1500)},a)};e.track.event=function(b,c,a,d,f,h){e.track({t:"event",ec:g(b,150),ea:g(c,500),el:g(a,500),ev:d,ni:f?1:0},h)};e.track.timing=function(b,c,a,d,f){36E5>a&&Math.random()<e.track.timingSamplingRate&&e.track({t:"timing",utc:g(b,150),utv:g(c,500),utt:a,utl:g(d,500)},f)};e.track.exception=
function(b,c,a,d){try{e.track.event(b,c.stack?c.stack.replace(/[\r\n]\s+\w+ /g," \x3e ").replace(/https?:\/\/[^/]+/g,"..."):c,a,null,null,d)}catch(f){}};e.trackResourcePerformance=function(b,c,a,d){var f=[];try{var e=h.performance.getEntriesByType("resource"),g,k;for(g=0;g<e.length;++g)k=e[g],b.test(k.name)&&(m(k,c,a,d),f.push(k))}catch(l){}return f};e.trackPagePerformance=function(b,c,a){try{m(h.performance.timing,b,c,a)}catch(d){}}})(window,"eaio",(window.eproxy||{trackingID:"UA-7427410-88"}).trackingID,
(window.eproxy||{hostName:location.hostname}).hostName);
(function(){function e(b){var a=b;try{a=/(https?\/.+)/i.exec(b)[1].replace(/^(https?)\//i,"$1://")}catch(c){}return a}function d(){var b=window.performance.getEntriesByType("resource"),a,c,d=e(location.href);for(a=0;a<b.length;++a)c=b[a],c.name.startsWith(location.origin)||c.name.startsWith("https://www.google-analytics.com/collect")||eaio.track.event("ResourceOnDifferentDomains",d,e(c.name))}/m/.test(document.readyState)?d():"undefined"!=typeof window.attachEvent?window.attachEvent("onload",d):window.addEventListener&&
window.addEventListener("load",d,!1)})();
(function(){function a(){eaio.trackPagePerformance("Proxy")}/m/.test(document.readyState)?a():"undefined"!=typeof window.attachEvent?window.attachEvent("onload",a):window.addEventListener&&window.addEventListener("load",a,!1)})();
