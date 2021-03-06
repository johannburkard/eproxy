package com.eaio.eproxy.cache.memcached

import static org.hamcrest.MatcherAssert.*
import static org.hamcrest.Matchers.*

import java.util.concurrent.Future
import java.util.concurrent.TimeoutException

import org.apache.http.client.cache.HttpCacheEntry
import org.junit.Before
import org.junit.Test

import com.google.appengine.api.memcache.AsyncMemcacheService

/**
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
class AsyncMemcacheServiceHttpCacheStorageTest {

    @Lazy
    AsyncMemcacheServiceHttpCacheStorage asyncMemcacheServiceHttpCacheStorage
    
    @Before
    void 'set up mock service'() {
        asyncMemcacheServiceHttpCacheStorage.@asyncMemcacheService = [ get: { [ get: { null } ] as Future<Object> }, delete: { [ get: { true } ] as Future<Boolean> },
            put: { String key, HttpCacheEntry entry -> [ get: {} ] as Future<Void> } ] as AsyncMemcacheService 
    }
    
    @Test
    void 'should return null if key not present'() {
        assertThat(asyncMemcacheServiceHttpCacheStorage.getEntry('http://foo.com/bar.html'), nullValue())
    }
    
    @Test
    void 'getEntry should cache calls'() {
        assertThat(asyncMemcacheServiceHttpCacheStorage.getEntry('http://foo.com/bar.html'), nullValue())
        asyncMemcacheServiceHttpCacheStorage.@asyncMemcacheService = [ ] as AsyncMemcacheService
        assertThat(asyncMemcacheServiceHttpCacheStorage.getEntry('http://foo.com/bar.html'), nullValue())
    }
    
    @Test
    void 'removeEntry should cache calls'() {
        asyncMemcacheServiceHttpCacheStorage.removeEntry('http://foo.com/bar.html')
        asyncMemcacheServiceHttpCacheStorage.@asyncMemcacheService = [ ] as AsyncMemcacheService
        assertThat(asyncMemcacheServiceHttpCacheStorage.getEntry('http://foo.com/bar.html'), nullValue())
    }
    
    @Test
    void 'putEntry should cause calls to be cached'() {
        asyncMemcacheServiceHttpCacheStorage.putEntry('http://foo.com/bar.html', null)
        asyncMemcacheServiceHttpCacheStorage.@asyncMemcacheService = [ ] as AsyncMemcacheService
        asyncMemcacheServiceHttpCacheStorage.getEntry('http://foo.com/bar.html')
    }
    
    @Test
    void 'InterruptedException should interrupt the thread'() {
        asyncMemcacheServiceHttpCacheStorage.@asyncMemcacheService = [ get: { [ get: { throw new InterruptedException() } ] as Future<Object> } ] as AsyncMemcacheService
        asyncMemcacheServiceHttpCacheStorage.getEntry('uiuiui')
        assertThat(Thread.currentThread().interrupted(), is(true)) // Do not use .isInterrupted() or .interrupted, see https://github.com/jacoco/jacoco/issues/394
    }
    
}
