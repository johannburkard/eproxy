package com.eaio.eproxy.rewriting.html

import static org.hamcrest.MatcherAssert.*
import static org.hamcrest.Matchers.*

import org.apache.xerces.xni.parser.XMLDocumentFilter
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.xml.sax.InputSource
import org.xml.sax.XMLReader

import com.eaio.eproxy.entities.RewriteConfig
import com.eaio.eproxy.rewriting.Rewriting

/**
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
class MetaRewritingFilterTest {
    
    @Rule
    public ErrorCollector errorCollector = new ErrorCollector()
    
    @Lazy
    MetaRewritingFilter metaRewritingFilter = new MetaRewritingFilter(baseURI: 'http://rah.com/'.toURI(),
        requestURI: 'https://www.facebook.com/'.toURI(), rewriteConfig: RewriteConfig.fromString('rnw'))
    
    @Test
    void '<meta refresh> should be rewritten'() {
        StringWriter output = new StringWriter()
        XMLReader xmlReader = new Rewriting().newHTMLReader()
        XMLDocumentFilter[] filters = [ metaRewritingFilter, new org.cyberneko.html.filters.Writer(output, 'UTF-8') ].toArray()
        xmlReader.setProperty('http://cyberneko.org/html/properties/filters', filters)
        xmlReader.parse(new InputSource(characterStream: new FileReader(new File('src/test/resources/com/eaio/eproxy/rewriting/html/bla.html'))))
        errorCollector.checkThat(output as String, containsString('<meta http-equiv="refresh" content="50; url=//rah.com/rnw-https/www.facebook.com/blorb.html"'))
        errorCollector.checkThat(output as String, not(containsString('60;url=ratte.html')))
    }
    
    @Test
    void 'Baidu\'s <meta refresh> should be rewritten'() {
        StringWriter output = new StringWriter()
        XMLReader xmlReader = new Rewriting().newHTMLReader()
        XMLDocumentFilter[] filters = [ metaRewritingFilter, new org.cyberneko.html.filters.Writer(output, 'UTF-8') ].toArray()
        xmlReader.setProperty('http://cyberneko.org/html/properties/filters', filters)
        xmlReader.parse(new InputSource(characterStream: new FileReader(new File('src/test/resources/com/eaio/eproxy/rewriting/html/baidu-redirect.html'))))
        errorCollector.checkThat(output as String, containsString('<meta http-equiv="refresh" content="0;URL=\'//rah.com/rnw-http/www.n-tv.de/\'"'))
    }

}
