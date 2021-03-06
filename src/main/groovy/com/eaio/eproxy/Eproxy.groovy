package com.eaio.eproxy

import java.util.concurrent.TimeUnit
import java.net.Proxy

import javax.net.ssl.SSLException

import org.apache.commons.codec.binary.Base64
import org.apache.http.HttpRequestInterceptor
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.protocol.RequestAcceptEncoding
import org.apache.http.client.protocol.RequestClientConnControl
import org.apache.http.client.protocol.RequestExpectContinue
import org.apache.http.client.protocol.ResponseContentEncoding
import org.apache.http.config.Registry
import org.apache.http.config.RegistryBuilder
import org.apache.http.config.SocketConfig
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.cookie.CookieSpec
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler
import org.apache.http.impl.client.cache.CacheConfig
import org.apache.http.impl.client.cache.CachingHttpClientBuilder
import org.apache.http.impl.client.cache.CachingHttpClients
import org.apache.http.impl.conn.*
import org.apache.http.impl.cookie.RFC6265LaxSpec
import org.apache.http.protocol.*
import org.apache.http.ssl.SSLContexts
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.web.DispatcherServletAutoConfiguration
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration.WebMvcAutoConfigurationAdapter
import org.springframework.context.annotation.*
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry

import com.eaio.eproxy.cache.memcached.AsyncMemcacheServiceHttpCacheStorage
import com.eaio.net.httpclient.*
import com.eaio.util.googleappengine.NotOnGoogleAppEngineOrDevserver
import com.eaio.util.googleappengine.OnGoogleAppEngineOrDevserver
import com.google.appengine.api.memcache.AsyncMemcacheService
import com.google.appengine.api.memcache.MemcacheServiceFactory
import com.j256.simplemagic.ContentInfoUtil
/**
 * eproxy configuration.
 *
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id: EaioWeb.groovy 7254 2015-05-19 10:15:33Z johann $
 */
@ComponentScan('com.eaio.eproxy')
@Configuration
@EnableAutoConfiguration
@EnableWebMvc
class Eproxy extends WebMvcAutoConfigurationAdapter {

    // HttpClient

    @Value('${http.clientConnectionTimeout}')
    Long clientConnectionTimeout

    @Value('${http.maxTotalSockets}')
    Integer maxTotalSockets

    @Value('${http.maxSocketsPerRoute}')
    Integer maxSocketsPerRoute

    @Value('${http.connectionTimeout}')
    Integer connectionTimeout

    @Value('${http.connectionRequestTimeout}')
    Integer connectionRequestTimeout

    @Value('${http.readTimeout}')
    Integer readTimeout

    @Value('${http.retryCount}')
    Integer retryCount

    @Value('${http.validateSSL}')
    Boolean validateSSL

    @Value('${http.userAgent}')
    String userAgent

    @Value('${http.validateAfterInactivity}')
    Integer validateAfterInactivity

    // SOCKS proxy

    @Value('${proxy.socks.host}')
    String proxySOCKSHost

    @Value('${proxy.socks.port}')
    Integer proxySOCKSPort // Character not supported
    
    // Socket Settings
    
    @Value('${socket.backlog}')
    int socketBacklog
    
    @Value('${socket.receiveBuffer}')
    int socketReceiveBuffer
    
    @Value('${socket.sendBuffer}')
    int socketSendBuffer
    
    @Value('${socket.keepAlive}')
    boolean socketKeepAlive
    
    @Value('${socket.linger}')
    int socketLinger
    
    @Value('${socket.noDelay}')
    boolean socketNoDelay 
    
    // Caching

    @Value('${cache.maxCacheEntries}')
    Integer maxEntries

    @Value('${cache.maxObjectSize}')
    Integer maxObjectSize
    
    @Value('${cache.shared}')
    boolean cacheIsShared
    
    // Socket settings
    
    @Autowired(required = false)
    AsyncMemcacheServiceHttpCacheStorage asyncMemcacheServiceHttpCacheStorage

    /**
     * Not necessary, hence stubbed out.
     * @see org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration.WebMvcAutoConfigurationAdapter#addViewControllers(org.springframework.web.servlet.config.annotation.ViewControllerRegistry)
     */
    @Override
    void addViewControllers(ViewControllerRegistry registry) {
    }

    @Lazy
    @Bean(destroyMethod = 'close')
    HttpClient httpClient() {
        Registry<ConnectionSocketFactory> socketFactoryRegistry = connectionSocketFactory()

        if (!OnGoogleAppEngineOrDevserver.CONDITION) {
            if (proxySOCKSHost && proxySOCKSPort) {
                Proxy socksProxy = new Proxy(Proxy.Type.SOCKS,
                    InetSocketAddress.createUnresolved(proxySOCKSHost, proxySOCKSPort ?: 1080I))

                socketFactoryRegistry = wrapConnectionSocketFactory(socketFactoryRegistry, socksProxy)
            }
        }

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry,
            ManagedHttpClientConnectionFactory.INSTANCE, DefaultSchemePortResolver.INSTANCE,
            new TimingDnsResolver(SystemDefaultDnsResolver.INSTANCE), clientConnectionTimeout ?: 60000L, TimeUnit.MILLISECONDS)
        connectionManager.with {
            maxTotal = maxTotalSockets ?: 32I
            defaultMaxPerRoute = maxSocketsPerRoute ?: 6I
        }
        connectionManager.validateAfterInactivity = validateAfterInactivity ?: 10000I

        // TODO: CachingExec - Via-Header-Erzeugung :(
            
        CachingHttpClientBuilder builder = CachingHttpClients.custom()
            .setConnectionManager(connectionManager)
            // Retries. InterruptedIOException is allowed to be retried.
            .setRetryHandler(new DefaultHttpRequestRetryHandler(retryCount ?: 0I, true, [ UnknownHostException, ConnectException, SSLException ]))
            .setDefaultRequestConfig(requestConfig())
            .setCacheConfig(cacheConfig())
            .setDefaultSocketConfig(socketConfig())
            .setHttpProcessor(httpProcessor())
            .disableRedirectHandling()
            
        if (userAgent) {
            builder.userAgent = userAgent
        }
        if (asyncMemcacheServiceHttpCacheStorage) {
            builder.httpCacheStorage = asyncMemcacheServiceHttpCacheStorage
        }
        if (retryCount == 0I) {
            builder.disableAutomaticRetries()
        }

        CloseableHttpClient client = builder.build()
        client
    }

    @Bean
    Registry<ConnectionSocketFactory> connectionSocketFactory() {
        RegistryBuilder.<ConnectionSocketFactory>create()
            .register('http', PlainConnectionSocketFactory.socketFactory)
            .register('https', validateSSL ? SSLConnectionSocketFactory.systemSocketFactory : new SSLConnectionSocketFactory(SSLContexts.createSystemDefault(), NoopHostnameVerifier.INSTANCE)) // TODO Funktioniert das?
            .build()
    }
    
    Registry<ConnectionSocketFactory> wrapConnectionSocketFactory(ConnectionSocketFactory delegate, Proxy socksProxy) {
        RegistryBuilder.<ConnectionSocketFactory>create()
            .register('http', new SOCKSProxyConnectionSocketFactory(delegate.lookup('http'), socksProxy))
            .register('https', new SOCKSProxyLayeredConnectionSocketFactory(delegate.lookup('https'), socksProxy))
            .build()
    }
    
    // Timeouts and redirect counts
    @Bean
    RequestConfig requestConfig() {
        RequestConfig.custom()
            .setConnectTimeout(connectionTimeout ?: 10000I)
            .setConnectionRequestTimeout(connectionRequestTimeout ?: 0I)
            .setSocketTimeout(readTimeout ?: 10000I)
            .setMaxRedirects(0I)
            .build()
    }
    
    // Caching
    @Bean
    CacheConfig cacheConfig() {
        CacheConfig.custom()
            .setMaxCacheEntries(maxEntries ?: 1000I)
            .setMaxObjectSize(maxObjectSize ?: 1I << 20I)
            .setSharedCache(OnGoogleAppEngineOrDevserver.CONDITION ? true : cacheIsShared)
            .setAsynchronousWorkersMax(0I)
            .build()
    }
    
    // Socket Settings
    SocketConfig socketConfig() {
        SocketConfig.custom()
            .setBacklogSize(socketBacklog)
            .setRcvBufSize(socketReceiveBuffer)
            .setSndBufSize(socketSendBuffer)
            .setSoKeepAlive(socketKeepAlive)
            .setSoLinger(socketLinger)
            .setTcpNoDelay(socketNoDelay)
            .build()
    }
    
    // Timeouts, timing and redirect counts
    @Bean
    HttpProcessor httpProcessor() {
        // Timing
        TimingInterceptor timingInterceptor = new TimingInterceptor()

        HttpProcessorBuilder builder = HttpProcessorBuilder.create()
            .addFirst((HttpRequestInterceptor) timingInterceptor)
            .addAll(new RequestContent(), new RequestTargetHost(), new RequestClientConnControl())
            
        if (userAgent) {
            builder.addAll(new RequestUserAgent(userAgent))
        }
        
        builder.addAll(new RequestExpectContinue(), new RequestAcceptEncoding())
            .addAll(new ResponseContentEncoding(), timingInterceptor)
            .build()
    }

    @Bean
    ReEncoding reEncoding() {
        new ReEncoding()
    }

    /**
     * Only enabled in Spring Boot's server. Keep this in sync with web.xml
     */
    @Bean(name = DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)
    @Conditional(NotOnGoogleAppEngineOrDevserver)
    DispatcherServlet dispatcherServlet() {
        new DispatcherServlet(publishEvents: false)
    }

    @Bean
    @Conditional(OnGoogleAppEngineOrDevserver)
    @Lazy
    AsyncMemcacheService asyncMemcacheService() {
        MemcacheServiceFactory.asyncMemcacheService
    }
    
    @Bean
    @Conditional(NotOnGoogleAppEngineOrDevserver)
    @Lazy
    Timer timer() {
        new Timer('eproxy', true)
    }
    
    @Bean
    CookieSpec cookieSpec() {
        new RFC6265LaxSpec()
    }
    
    @Bean
    Base64 base64() {
        new Base64(0I, null, true)
    }
    
    @Bean
    ContentInfoUtil contentInfoUtil() {
        new ContentInfoUtil()
    }

    static main(args) {
        SpringApplication.run(Eproxy, args)
    }

}
