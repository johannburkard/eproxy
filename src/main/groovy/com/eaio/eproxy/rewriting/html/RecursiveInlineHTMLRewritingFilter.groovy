package com.eaio.eproxy.rewriting.html

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.charset.Charset

import org.apache.xerces.xni.Augmentations
import org.apache.xerces.xni.QName
import org.apache.xerces.xni.XMLAttributes
import org.apache.xerces.xni.XNIException
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import com.eaio.eproxy.rewriting.Rewriting

/**
 * Transforms HTML as follows:
 * <ul>
 * <li>Any inline HTML is run through {@link Rewriting}
 * </ul>
 * 
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
@CompileStatic
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
class RecursiveInlineHTMLRewritingFilter extends RewritingFilter implements BeanFactoryAware {
    
    @Lazy
    private Charset defaultCharset = Charset.forName('UTF-8')
    
    // For some reason, reusing the Rewriting instance doesn't work so this class needs its own Rewriting instance.
    @Lazy
    private Rewriting rewriting = new Rewriting(beanFactory: beanFactory)

    BeanFactory beanFactory
    
    @Override
    void startElement(QName element, XMLAttributes attributes,
            Augmentations augs) throws XNIException {
        if (nameIs(element, 'iframe')) {
            rewriteElement(element, attributes, augs)
        }
        super.startElement(element, attributes, augs)
    }

    private void rewriteElement(QName qName, XMLAttributes atts, Augmentations augs) {
        int srcDocIndex = atts.getIndex('srcdoc')
        if (srcDocIndex >= 0I) {
            ByteArrayOutputStream bOut = new ByteArrayOutputStream()
            rewriting.rewriteHTMLFragment(new ByteArrayInputStream(atts.getValue(srcDocIndex).getBytes(defaultCharset)), bOut, defaultCharset, baseURI, requestURI, rewriteConfig)
            String rewrittenFragment = bOut.toString(defaultCharset.name())
            log.trace('rewrote srcdoc attribute from\n{}\nto\n{}', atts.getValue(srcDocIndex), rewrittenFragment)
            atts.setValue(srcDocIndex, rewrittenFragment)
        }
    }

}