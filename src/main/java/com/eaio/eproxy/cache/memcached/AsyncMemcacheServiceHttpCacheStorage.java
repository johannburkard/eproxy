package com.eaio.eproxy.cache.memcached;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.eaio.util.googleappengine.OnGoogleAppEngineOrDevserver;
import com.google.appengine.api.memcache.AsyncMemcacheService;
import com.google.appengine.api.memcache.MemcacheService.IdentifiableValue;

/**
 * {@link HttpCacheStorage} implementation that uses Google App Engine's {@link AsyncMemcacheService}.
 * 
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
@Component
@Conditional(OnGoogleAppEngineOrDevserver.class)
public class AsyncMemcacheServiceHttpCacheStorage implements HttpCacheStorage {

    private Logger log = LoggerFactory.getLogger(AsyncMemcacheServiceHttpCacheStorage.class);

    @Autowired(required = false)
    private AsyncMemcacheService asyncMemcacheService;

    @Value("${cache.memcacheMaxUpdateRetries}")
    private int maxUpdateRetries;

    @Value("${cache.memcacheTimeout}")
    private Integer memcacheTimeout;
    
    private ThreadLocal<MemcacheStatus> memcacheStatuses = new ThreadLocal<MemcacheStatus>();

    /**
     * @see org.apache.http.client.cache.HttpCacheStorage#putEntry(java.lang.String, org.apache.http.client.cache.HttpCacheEntry)
     */
    @Override
    public void putEntry(String key, HttpCacheEntry entry) throws IOException {
        log.trace("putEntry {}", key);
        Future<Void> future = asyncMemcacheService.put(key, entry);
        awaitFutureUntilTimeout("put", key, future);
        memcacheStatuses.set(new MemcacheStatus(key, entry != null));
    }

    /**
     * @see org.apache.http.client.cache.HttpCacheStorage#getEntry(java.lang.String)
     */
    @Override
    public HttpCacheEntry getEntry(String key) throws IOException {
        log.trace("getEntry {}", key);
        MemcacheStatus keyStatus = memcacheStatuses.get();
        HttpCacheEntry out;
        if (keyStatus != null && keyStatus.key.equals(key) && !keyStatus.cached) {
            out = null;
        }
        else {
            Future<Object> future = asyncMemcacheService.get(key);
            out = (HttpCacheEntry) awaitFutureUntilTimeout("get", key, future);
            memcacheStatuses.set(new MemcacheStatus(key, out != null));
        }
        return out;
    }

    /**
     * @see org.apache.http.client.cache.HttpCacheStorage#removeEntry(java.lang.String)
     */
    @Override
    public void removeEntry(String key) throws IOException {
        log.trace("removeEntry {}", key);
        Future<Boolean> future = asyncMemcacheService.delete(key);
        awaitFutureUntilTimeout("delete", key, future);
        memcacheStatuses.set(new MemcacheStatus(key, false));
    }

    /**
     * @see org.apache.http.client.cache.HttpCacheStorage#updateEntry(java.lang.String, org.apache.http.client.cache.HttpCacheUpdateCallback)
     */
    public void updateEntry(String key, HttpCacheUpdateCallback callback)
            throws IOException, HttpCacheUpdateException {
        for (int i = 0; i < maxUpdateRetries; ++i) {
            log.debug("updateEntry {} (try {}/{})", key, i, maxUpdateRetries);
            Future<IdentifiableValue> identifiableFuture = asyncMemcacheService.getIdentifiable(key);
            IdentifiableValue identifiable = awaitFutureUntilTimeout("updateEntry > getIdentifiable", key, identifiableFuture);
            if (identifiable == null && Thread.currentThread().isInterrupted()) {
                return;
            }
            HttpCacheEntry oldEntry = identifiable == null ? null : (HttpCacheEntry) identifiable.getValue();
            HttpCacheEntry newEntry = callback.update(oldEntry);
            if (identifiable == null) {
                if (newEntry != null) {
                    Future<Void> putFuture = asyncMemcacheService.put(key, newEntry);
                    awaitFutureUntilTimeout("updateEntry > put", key, putFuture);
                }
                memcacheStatuses.set(new MemcacheStatus(key, newEntry != null));
                return;
            }
            else {
                Future<Boolean> putFuture = asyncMemcacheService.putIfUntouched(key, identifiable, newEntry);
                Boolean stored = awaitFutureUntilTimeout("updateEntry > putIfUntouched", key, putFuture);
                if (stored == null && Thread.currentThread().isInterrupted()) {
                    return;
                }
                else if (Boolean.TRUE.equals(stored)) {
                    memcacheStatuses.set(new MemcacheStatus(key, true));
                    return;
                }
            }
        }
        throw new HttpCacheUpdateException(String.format("Failed to update %s after %d tries", key, maxUpdateRetries));
    }

    private <T> T awaitFutureUntilTimeout(String operation, String key, Future<T> future) throws IOException {
        T out = null;
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            out = memcacheTimeout == null ? future.get() : future.get(memcacheTimeout, TimeUnit.MILLISECONDS);
            log.debug("{} on key {} took {} ms", operation, key, stopWatch.getTime());
        }
        catch (InterruptedException ex) {
            log.warn("{} on key {} was interrupted after {} ms", operation, key, stopWatch.getTime());
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException ex) {
            throw new IOException(ex.getCause());
        }
        catch (TimeoutException ex) {
            log.warn("{} on key {} timed out after {} ms", operation, key, stopWatch.getTime());
        }
        return out;
    }
    
    /**
     * Encapsulates the result of a {@link AsyncMemcacheServiceHttpCacheStorage#getEntry(String)} call.
     * Used to prevent repeat <tt>getEntry</tt> calls from going to Memcache every time.
     */
    private static class MemcacheStatus {
        
        private final String key;
        
        private final boolean cached;
        
        private MemcacheStatus(String key, boolean cached) {
            this.key = key;
            this.cached = cached;
        }
        
    }

}
