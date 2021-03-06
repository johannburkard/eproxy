package com.eaio.eproxy.rewriting.css

import static org.apache.commons.lang3.StringUtils.*
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.regex.Pattern

import org.apache.xerces.xni.Augmentations
import org.apache.xerces.xni.QName
import org.apache.xerces.xni.XMLAttributes
import org.apache.xerces.xni.XMLString
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import org.w3c.css.sac.*
import org.w3c.dom.css.*

import com.eaio.eproxy.rewriting.URIManipulation
import com.eaio.eproxy.rewriting.html.RewritingFilter
import com.steadystate.css.dom.*

/**
 * Rewrites CSS and SVG using regular expressions because there are no robust Java CSS parsers.
 * 
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
class CSSRewritingFilter extends RewritingFilter implements URIManipulation {

    @Lazy
    private static Pattern patternURLImage = {
        //  Pattern.compile("(?:url|image)\\s*\\(\\s*(.*?)\\s*\\)", Pattern.CASE_INSENSITIVE)
        use (CSSEscapeRegEx) {
            StringBuilder builder = new StringBuilder()
            builder.append('(?:').appendRegEx('url').append('|').appendRegEx('image')
                    .append(')\\s*').appendRegEx('(', true).append('\\s*(.*?)\\s*').appendRegEx(')', true)
            Pattern.compile(builder as String, Pattern.CASE_INSENSITIVE)
        }
    }()

    @Lazy
    private static Pattern patternImport = {
        //  Pattern.compile("@import\\s*(.*?)\\s*(;|$)", Pattern.CASE_INSENSITIVE)
        use (CSSEscapeRegEx) {
            StringBuilder builder = new StringBuilder()
            builder.appendRegEx('@import').append('\\s*(.*?)\\s*(').appendRegEx(';').append('|$)')
            Pattern.compile(builder as String, Pattern.CASE_INSENSITIVE)
        }
    }()

    @Lazy
    private static Pattern patternSrcColorSpace = {
        //  Pattern.compile("\\W(?:src|colorSpace)\\s*=\\s*(.*?)\\s*(,|;|$)", Pattern.CASE_INSENSITIVE)
        use (CSSEscapeRegEx) {
            StringBuilder builder = new StringBuilder()
            builder.append('\\W(?:').appendRegEx('src').append('|').appendRegEx('colorSpace')
                    .append(')\\s*=\\s*(.*?)\\s*(').appendRegEx(',').append('|').appendRegEx(';').append('|$)')
            Pattern.compile(builder as String, Pattern.CASE_INSENSITIVE)
        }
    }()

    @Lazy
    private CSSUnescaper cssUnescaper

    private boolean inStyleElement,
        inSVGElement

    /**
     * Rewrites any style attributes, too.
     * 
     * @see org.cyberneko.html.filters.DefaultFilter#startElement(org.apache.xerces.xni.QName, org.apache.xerces.xni.XMLAttributes, org.apache.xerces.xni.Augmentations)
     */
    @CompileStatic
    @Override
    void startElement(QName qName, XMLAttributes atts, Augmentations augs) {
        if (nameIs(qName, 'style')) {
            inStyleElement = true
        }
        else if (nameIs(qName, 'svg')) {
            inSVGElement = true
        }
        rewriteElement(qName, atts, augs)
        super.startElement(qName, atts, augs)
    }

    @CompileStatic
    @Override
    void emptyElement(QName qName, XMLAttributes atts, Augmentations augs) {
        rewriteElement(qName, atts, augs)
        super.emptyElement(qName, atts, augs)
    }

    @CompileStatic
    @Override
    void endElement(QName qName, Augmentations augs) {
        if (nameIs(qName, 'style')) {
            inStyleElement = false
        }
        else if (nameIs(qName, 'svg')) {
            inSVGElement = false
        }
        super.endElement(qName, augs)
    }

    @CompileStatic
    private void rewriteElement(QName qName, XMLAttributes atts, Augmentations augs) {
        if (inSVGElement) {
            for (int i = 0; i < atts.length; ++i) {
                if (!atts.getPrefix(i)?.equalsIgnoreCase('xmlns') && !atts.getQName(i)?.equalsIgnoreCase('xmlns')
                && !atts.getLocalName(i)?.equalsIgnoreCase('href')) { // Don't rewrite xmlns namespaced attributes.
                    String attributeValue = atts.getValue(i)
                    if (isNotBlank(attributeValue)) {
                        String rewritten = rewriteCSS(attributeValue)
                        atts.setValue(i, rewritten)
                        log.debug('rewrote {} attribute {} chars to {} chars', atts.getQName(i), attributeValue.length(), rewritten.length())
                    }
                }
            }
        }
        else {
            String css = atts.getValue('style')
            if (isNotBlank(css)) {
                String rewritten = rewriteCSS(css)
                atts.setValue(atts.getIndex('style'), rewritten)
                log.debug('rewrote style attribute {} chars to {} chars', css.length(), rewritten.length())
            }
        }
    }

    /**
     * Rewrites <tt>&lt;style&gt;</tt> contents.
     * 
     * @see org.cyberneko.html.filters.DefaultFilter#characters(org.apache.xerces.xni.XMLString, org.apache.xerces.xni.Augmentations)
     */
    @CompileStatic
    @Override
    void characters(XMLString xmlString, Augmentations augs) {
        if (inStyleElement) {
            String css = new String(xmlString.ch, xmlString.offset, xmlString.length) // TODO: performance
            if (isNotBlank(css)) {
                String rewritten = rewriteCSS(css)
                super.characters(new XMLString(rewritten.toCharArray(), 0I, rewritten.length()), augs)
                log.debug('rewrote CSS {} chars to {} chars', xmlString/*.length*/, rewritten/*.length()*/)
            }
        }
        else {
            super.characters(xmlString, augs)
        }
    }

    /**
     * Rewrites CSS by unescaping any HTML remains and scanning for certain patterns by regular expression.
     * <p>
     * Check if <tt>css</tt> is blank before calling this.
     * 
     * @return never <code>null</code>
     */
    @CompileStatic
    String rewriteCSS(String css) {
        String out = trimToEmpty(css)
        if (out?.length() > 5I) { // Skip anything less than 6 characters.
            String unescapedCSS = unescapeHTML(css) ?: ''
            out = replacePatterns(unescapedCSS)
        }
        out
    }

    @CompileStatic
    String unescapeHTML(String css) {
        CSSEscapeUtils.unescapeHTML(css)
    }

    @CompileStatic
    String replacePatterns(String css) {
        [ patternURLImage, patternImport, patternSrcColorSpace ].inject(css, { String s, Pattern p ->
            s.replaceAll(p, { List<String> matches ->
                String out = matches[0I]
                String uri = matches[1I], unescapedURI = cssUnescaper.translate(uri)
                if (unescapedURI.startsWith('"') || unescapedURI.startsWith('\'')) {
                    unescapedURI = removeStart(unescapedURI, '"')
                    unescapedURI = removeStart(unescapedURI, '\'')
                    uri = uri.substring(1I)
                }
                if (unescapedURI.endsWith('"') || unescapedURI.endsWith('\'')) {
                    unescapedURI = removeEnd(unescapedURI, '"')
                    unescapedURI = removeEnd(unescapedURI, '\'')
                    uri = uri.substring(0I, uri.length() - 1I)
                }
                if (attributeValueNeedsRewriting(unescapedURI)) {
                    String rewritten = encodeTargetURI(baseURI, requestURI, unescapedURI, rewriteConfig)
                    out = replace(matches[0I], uri, rewritten)
                }
                out
            })
        })
    }

}
