package com.eaio.eproxy.api

import static com.eaio.eproxy.RequestMocks.*
import static org.hamcrest.MatcherAssert.*
import static org.hamcrest.Matchers.*

import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.SpringApplicationConfiguration
import org.springframework.boot.test.WebIntegrationTest
import org.springframework.mock.web.DelegatingServletOutputStream
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner

import com.eaio.eproxy.Eproxy

/**
 * Simulates user agents with enabled cookie support.
 * 
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
@RunWith(SpringJUnit4ClassRunner)
@SpringApplicationConfiguration(classes = Eproxy)
@WebIntegrationTest(value = [ 'cookies.enabled=true' ], randomPort = true)
class ProxyHTTPCookiesIT {

    @Autowired
    Proxy proxy
    
    @Test
    void 'should support cookies'() {
        HttpServletRequest request = buildHttpServletRequest('http://www.bing.com')
        ByteArrayOutputStream bOut = new ByteArrayOutputStream()
        boolean cookieSet = false
        HttpServletResponse response = [
            setStatus: { int status -> assertThat(status, is(200I)) },
            setHeader: { String name, String value -> },
            getOutputStream: { new DelegatingServletOutputStream(bOut) },
            isCommitted: { true },
            addCookie: { Cookie cookie -> cookieSet = true; assertThat(cookie.name, containsString('bing.com')) },
        ] as HttpServletResponse
        proxy.proxy('rnw', 'http', request, response)
        assertThat(cookieSet, is(true))
        assertThat(bOut.toString(0I), containsString('meta content'))
    }

}
