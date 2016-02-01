package com.eaio.eproxy.rewriting

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.nio.charset.Charset

import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.HeaderElement
import org.apache.xerces.xni.parser.XMLDocumentFilter;
import org.cyberneko.html.parsers.SAXParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.XMLReader

import com.eaio.eproxy.entities.RewriteConfig
import com.eaio.eproxy.rewriting.css.*
import com.eaio.eproxy.rewriting.html.*
import com.eaio.net.httpclient.ReEncoding

/**
 * Rewriting code for different MIME types.
 * 
 * @author <a href="mailto:johann@johannburkard.de">Johann Burkard</a>
 * @version $Id$
 */
@CompileStatic
@Component
@Slf4j
class Rewriting {
    
    @Autowired
    ReEncoding reEncoding
    
    @Lazy
    private Charset defaultCharset = Charset.forName('UTF-8')
    
    private Set<String> javascript = [
        'application/ecmascript',
        'application/javascript',
        'application/x-ecmascript',
        'application/x-javascript',
        'text/ecmascript',
        'text/javascript',
        'text/javascript1.0',
        'text/javascript1.1',
        'text/javascript1.2',
        'text/javascript1.3',
        'text/javascript1.4',
        'text/javascript1.5',
        'text/jscript',
        'text/livescript',
        'text/x-ecmascript',
        'text/x-javascript',
    ] as Set

    // TODO: Get rid of VBScript

    private Set<String> html = [
        'text/html',
        'text/x-server-parsed-html',
        'application/xml+xhtml',
    ] as Set

    boolean isHTML(String mimeType) {
        html.contains(mimeType?.toLowerCase() ?: '')
    }

    boolean isCSS(String mimeType) {
        mimeType?.equalsIgnoreCase('text/css')
    }
    
    boolean isSVG(String mimeType) {
        mimeType?.equalsIgnoreCase('image/svg+xml')
    }
    
    // TODO: Look at Content-Disposition header to prevent downloads from being rewritten
    boolean canRewrite(HeaderElement contentDisposition, RewriteConfig rewriteConfig, String mimeType) {
        rewriteConfig && (isHTML(mimeType) || isCSS(mimeType))
    }
    
    void rewrite(InputStream inputStream, OutputStream outputStream, Charset charset, URI baseURI, URI requestURI, RewriteConfig rewriteConfig, String mimeType) {
        if (isHTML(mimeType)) {
            rewriteHTML(inputStream, outputStream, charset, baseURI, requestURI, rewriteConfig)
        }
        else if (isCSS(mimeType)) {
            rewriteCSS(inputStream, outputStream, charset, baseURI, requestURI, rewriteConfig)
        }
    }
    
    void rewriteHTML(InputStream inputStream, OutputStream outputStream, Charset charset, URI baseURI, URI requestURI, RewriteConfig rewriteConfig) {
        Writer outputWriter = new OutputStreamWriter(outputStream, (Charset) charset ?: defaultCharset)
        XMLReader xmlReader = newXMLReader()
        XMLDocumentFilter[] filters = (XMLDocumentFilter[]) [
            new CSSRewritingContentHandler(reEncoding: reEncoding, baseURI: baseURI, requestURI: requestURI, rewriteConfig: rewriteConfig),
            new MetaRewritingContentHandler(reEncoding: reEncoding, baseURI: baseURI, requestURI: requestURI, rewriteConfig: rewriteConfig),
            new RemoveActiveContentContentHandler(),
            new RemoveNoScriptElementsContentHandler(),
            new ImgSrcsetRewritingContentHandler(reEncoding: reEncoding, baseURI: baseURI, requestURI: requestURI, rewriteConfig: rewriteConfig),
            new URIRewritingContentHandler(reEncoding: reEncoding, baseURI: baseURI, requestURI: requestURI, rewriteConfig: rewriteConfig),
            new org.cyberneko.html.filters.Writer(outputWriter, (charset ?: defaultCharset).name())
            ].toArray()
        xmlReader.setProperty('http://cyberneko.org/html/properties/filters', filters)
        try {
            xmlReader.parse(newSAXInputSource(inputStream, charset)) // TODO: BufferedInputStream?
        }
        catch (SAXException ex) {
            if (ExceptionUtils.getRootCause(ex) instanceof IOException) {
                throw ExceptionUtils.getRootCause(ex)
            }
            else {
                log.warn("While parsing {}@{}:{}: {}", requestURI, ''/*((DelegatingContentHandler) xmlReader.contentHandler).documentLocator.lineNumber*/,
                    ''/*((DelegatingContentHandler) xmlReader.contentHandler).documentLocator.columnNumber*/, (ExceptionUtils.getRootCause(ex) ?: ex).message)
            }
        }
        finally {
            try {
                outputWriter.flush()
            }
            catch (emall) {}
        }
    }
    
    void rewriteCSS(InputStream inputStream, OutputStream outputStream, Charset charset, URI baseURI, URI requestURI, RewriteConfig rewriteConfig) {
        Writer outputWriter = new OutputStreamWriter(outputStream, charset ?: defaultCharset)
        try {
            new CSSRewritingContentHandler(reEncoding: reEncoding, baseURI: baseURI, requestURI: requestURI, rewriteConfig: rewriteConfig)
                .rewriteCSS(newSACInputSource(inputStream, charset), outputWriter)
        }
        finally {
            try {
                outputWriter.flush()
            }
            catch (emall) {}
        }
    }
    
    XMLReader newXMLReader() {
        SAXParser out = new SAXParser()
        out.setFeature('http://cyberneko.org/html/features/balance-tags', false)
        out
    }
    
    InputSource newSAXInputSource(InputStream inputStream, Charset charset) {
        InputSource out = new InputSource(byteStream: inputStream)
        if (charset) {
            out.encoding = charset.displayName()
        }
        out
    }
    
    org.w3c.css.sac.InputSource newSACInputSource(InputStream inputStream, Charset charset) {
        org.w3c.css.sac.InputSource out = new org.w3c.css.sac.InputSource(byteStream: inputStream)
        if (charset) {
            out.encoding = charset.displayName()
        }
        out
    }
    
}
