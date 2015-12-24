package com.eaio.eproxy.api

import groovy.util.logging.Slf4j

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.http.Header
import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.cache.HttpCacheContext
import org.apache.http.client.methods.*
import org.apache.http.entity.InputStreamEntity
import org.apache.http.util.EntityUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

import com.eaio.net.httpclient.TimingInterceptor

/**
 * Proxies and optionally rewrites content.
 * 
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
@RestController
@Slf4j
class Proxy {
    
    @Value('${proxy.hostName}')
    String hostName
    
    @Autowired
    HttpClient httpClient

    @RequestMapping('/{scheme:https?}/**')
    void proxy(@PathVariable String scheme, HttpServletRequest request, HttpServletResponse response) {
        proxy(scheme, null, request, response)
    }
    
    @RequestMapping('/{rewriteConfig}-{scheme:https?}/**')
    void proxy(@PathVariable('scheme') String scheme, @PathVariable('rewriteConfig') String rewriteConfig, HttpServletRequest request, HttpServletResponse response) {
        URI requestURI = buildRequestURI(scheme, stripContextPathFromRequestURI(request.contextPath, request.requestURI), request.queryString)
        if (!requestURI.host) {
            response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE)
            return
        }

        HttpCacheContext context = HttpCacheContext.create()
        HttpResponse httpResponse
        try {
            HttpUriRequest uriRequest = newRequest(request.method, requestURI)
            if (uriRequest instanceof HttpEntityEnclosingRequest) {
                setRequestEntity(uriRequest, request.getHeader('Content-Length'), request.inputStream)
            }
            
            httpResponse = httpClient.execute(uriRequest, context)
            
            response.setStatus(httpResponse.statusLine.statusCode, httpResponse.statusLine.reasonPhrase)
            
            httpResponse.headerIterator().each { Header header ->
                if (header.name?.equalsIgnoreCase('Location')) { // TODO: Link
                    response.setHeader(header.name, rewriteLocationValue(header.value.toURI(), request.scheme, request.serverName, request.serverPort, request.contextPath) as String)                    
                }
                else {
                    response.setHeader(header.name, header.value)
                }
            }

            if (httpResponse.entity) {
                if (rewriteConfig) {
                    
                }
                else {
                    IOUtils.copyLarge(httpResponse.entity.content, response.outputStream) // Do not use HttpEntity#writeTo(OutputStream) -- doesn't get counted in all instances.
                }
            }            
        }
//        catch (UnknownHostException ex) {
//            // TODO: 404?
//        }
        catch (SocketException ex) {
            if (ex.message?.startsWith('Permission denied')) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.message)
            }
            else {
                throw ex
            }
        }
        finally {
            TimingInterceptor.log(context, log)
            EntityUtils.consumeQuietly(httpResponse?.entity)
        }
    }
    
    private HttpUriRequest newRequest(String method, URI uri) {
        switch (method) {
            case 'GET': return new HttpGet(uri)
            case 'DELETE': return new HttpDelete(uri)
            case 'HEAD': return new HttpHead(uri)
            case 'OPTIONS': return new HttpOptions(uri)
            case 'PATCH': return new HttpPatch(uri)
            case 'POST': return new HttpPost(uri)
            case 'PUT': return new HttpPut(uri)
            case 'TRACE': return new HttpTrace(uri)
        }
    }
    
    /**
     * Make sure to remove the context path before calling this method.
     */
    // TODO: Use ReEncodingRedirectStrategy
    URI buildRequestURI(String scheme, String requestURI, String queryString) {
        String host, path
        host = StringUtils.substringAfter(requestURI[1..-1], '/')
        path = StringUtils.substringAfter(host, '/') ?: '/'
        // TODO: Support for Ports
        UriComponentsBuilder.newInstance().scheme(scheme).host(StringUtils.substringBefore(host, '/')).path(path).query(queryString).build().toUri()
    }
    
    /**
     * Removes the context path prefix from <tt>requestURI</tt>.
     */
    String stripContextPathFromRequestURI(String contextPath, String requestURI) {
        contextPath ? StringUtils.substringAfter(requestURI, contextPath) : requestURI
    }
    
    URI rewriteLocationValue(URI locationValue, String requestScheme, String requestHost, int requestPort, String contextPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
            .scheme(requestScheme)
            .host(requestHost)
            .port(getPort(requestScheme, requestPort))
            .path(contextPath)
            .pathSegment(locationValue.scheme, locationValue.authority)
        if (locationValue.rawPath) {
            builder.path(locationValue.rawPath)
        }
        builder.query(locationValue.rawQuery).fragment(locationValue.rawFragment).build().toUri()
    }
    
    int getPort(String scheme, int port) {
        port == -1I || (scheme?.equalsIgnoreCase('http') && port == 80I) || (scheme?.equalsIgnoreCase('https') && port == 443I) ? -1I : port
    }
    
    void setRequestEntity(HttpEntityEnclosingRequest uriRequest, String contentLength, InputStream inputStream) {
        uriRequest.entity = contentLength?.integer ? new InputStreamEntity(inputStream, contentLength as int) : new InputStreamEntity(inputStream)
    }

}