package com.eaio.eproxy.rewriting

import static org.hamcrest.MatcherAssert.*
import static org.hamcrest.Matchers.*

import org.ccil.cowan.tagsoup.Parser
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.xml.sax.InputSource

/**
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
class RemoveActiveContentContentHandlerTest {
    
    @Rule
    public ErrorCollector errorCollector = new ErrorCollector()

    @Test
    void 'active content and on* handlers should be removed'() {
        StringWriter output = new StringWriter()
        Parser parser = new Parser()
        parser.contentHandler = new RemoveActiveContentContentHandler(delegate: new HTMLSerializer(output))
        parser.parse(new InputSource(characterStream: new FileReader(new File('src/test/resources/com/eaio/eproxy/rewriting/bla.html'))))
        errorCollector.checkThat(output as String, not(containsString('<script')))
        errorCollector.checkThat(output as String, not(containsString('<noscript/>')))
        errorCollector.checkThat(output as String, containsString('<img src="fnuh.jpg">'))
        errorCollector.checkThat(output as String, containsString('<img width="1" height="1" src="dah.jpg">'))
        errorCollector.checkThat(output as String, not(containsString('<rah ')))
    }

}