package com.eaio.eproxy.rewriting.html

import static org.apache.commons.lang3.StringUtils.*

import groovy.transform.CompileStatic

import org.apache.xerces.xni.Augmentations
import org.apache.xerces.xni.QName
import org.apache.xerces.xni.XMLAttributes
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * Replaces javascript: URIs with inactive values.
 * 
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
@CompileStatic
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class RemoveJavaScriptURIFilter extends BaseFilter {

    @Override
    void startElement(QName element, XMLAttributes attributes,
            Augmentations augs) {
        rewriteElement(element, attributes, augs)
        super.startElement(element, attributes, augs)
    }

    @Override
    void emptyElement(QName element, XMLAttributes attributes,
            Augmentations augs) {
        rewriteElement(element, attributes, augs)
        super.emptyElement(element, attributes, augs)
    }

    private void rewriteElement(QName qName, XMLAttributes atts, Augmentations augs) {
        int srcIndex = atts.getIndex('src')
        if (srcIndex >= 0I) {
            String value = trimToEmpty(atts.getValue(srcIndex))
            if (startsWithIgnoreCase(value, 'javascript:')) {
                atts.setValue(srcIndex, 'javascript:""')
            }
        }
    }

}
