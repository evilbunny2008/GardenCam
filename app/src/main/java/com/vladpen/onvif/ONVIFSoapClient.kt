package com.vladpen.onvif

import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream

class ONVIFSoapClient(
    serviceUrl: String,
    private val credentials: ONVIFCredentials?
) {
    companion object {
        private const val TIMEOUT = 10000
        private const val ONVIF_NAMESPACE = "http://www.onvif.org/ver10/device/wsdl"
        private const val SOAP_NAMESPACE = "http://www.w3.org/2003/05/soap-envelope"
        const val SCHEMA_NAMESPACE = "http://www.onvif.org/ver10/schema"
    }

    // Fix URL format issues
    private val serviceUrl = when {
        serviceUrl.startsWith("onvif://") -> serviceUrl.replace("onvif://", "http://")
        !serviceUrl.startsWith("http://") && !serviceUrl.startsWith("https://") -> "http://$serviceUrl"
        else -> serviceUrl
    }

    fun sendRequest(method: String, namespace: String = ONVIF_NAMESPACE, parameters: Map<String, Any> = emptyMap()): ONVIFResponse? {
        val parameterXml = parameters.entries.joinToString("") { (key, value) ->
            "<$key>$value</$key>"
        }
        return post(method, namespace, parameterXml)?.let { parseResponse(it) }
    }

    /**
     * Sends [bodyXml] as the children of the request element. Elements in [namespace] must use
     * the "tds" prefix and ONVIF schema types the "tt" prefix; both are declared on the envelope.
     * Returns a namespace-aware document, or null on a transport error or SOAP fault.
     */
    fun sendXmlRequest(method: String, namespace: String, bodyXml: String): Document? {
        val response = post(method, namespace, bodyXml) ?: return null
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(response.toByteArray()))
            if (document.getElementsByTagNameNS(SOAP_NAMESPACE, "Fault").length > 0) null else document
        } catch (e: Exception) {
            android.util.Log.e("ONVIF", "Failed to parse $method response: ${e.message}")
            null
        }
    }

    private fun post(method: String, namespace: String, parameterXml: String): String? {
        return try {
            android.util.Log.d("ONVIF", "Sending SOAP request: $method to $serviceUrl")
            
            // Validate inputs
            val safeMethod = ONVIFSecurity.validateInput(method)
            val safeNamespace = ONVIFSecurity.validateInput(namespace)
            
            if (!ONVIFSecurity.isValidUrl(serviceUrl)) {
                android.util.Log.e("ONVIF", "Invalid service URL: $serviceUrl")
                return null
            }

            val soapEnvelope = createSoapEnvelope(safeMethod, safeNamespace, parameterXml)
            android.util.Log.d("ONVIF", "SOAP envelope created, sending HTTP request")
            
            val response = sendHttpRequest(soapEnvelope)
            
            if (response != null) {
                android.util.Log.d("ONVIF", "Received SOAP response: ${response.take(200)}...")
            } else {
                android.util.Log.e("ONVIF", "No response received from ONVIF device")
            }
            response
        } catch (e: Exception) {
            android.util.Log.e("ONVIF", "SOAP request failed: ${e.message}")
            null
        }
    }

    private fun createSoapEnvelope(method: String, namespace: String, parameterXml: String): String {
        val securityHeader = credentials?.let { createWSSecurityHeader(it) } ?: ""

        return """<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="$SOAP_NAMESPACE" xmlns:tds="$namespace" xmlns:tt="$SCHEMA_NAMESPACE">
    <soap:Header>
        $securityHeader
    </soap:Header>
    <soap:Body>
        <tds:$method>
            $parameterXml
        </tds:$method>
    </soap:Body>
</soap:Envelope>"""
    }

    private fun createWSSecurityHeader(creds: ONVIFCredentials): String {
        val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        val nonceBytes = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toByteArray()
        val nonceBase64 = android.util.Base64.encodeToString(nonceBytes, android.util.Base64.NO_WRAP)
        val digest = createPasswordDigest(nonceBytes, created, creds.password)

        return """
        <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <wsse:UsernameToken>
                <wsse:Username>${creds.username}</wsse:Username>
                <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$digest</wsse:Password>
                <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">$nonceBase64</wsse:Nonce>
                <wsu:Created xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">$created</wsu:Created>
            </wsse:UsernameToken>
        </wsse:Security>
        """.trimIndent()
    }

    private fun createPasswordDigest(nonceBytes: ByteArray, created: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val input = nonceBytes + created.toByteArray() + password.toByteArray()
        return android.util.Base64.encodeToString(digest.digest(input), android.util.Base64.NO_WRAP)
    }

    private fun sendHttpRequest(soapEnvelope: String): String? {
        return try {
            val url = URL(serviceUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "")
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(soapEnvelope.toByteArray())
            }

            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    input.bufferedReader().readText()
                }
            } else {
                // Try to read error response for debugging
                try {
                    connection.errorStream?.use { error ->
                        error.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    // Ignore error reading error response
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseResponse(xmlResponse: String): ONVIFResponse? {
        return try {
            val sanitized = ONVIFSecurity.sanitizeDeviceResponse(xmlResponse)
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(ByteArrayInputStream(sanitized.toByteArray()))
            
            ONVIFResponse(document)
        } catch (e: Exception) {
            null
        }
    }
}

class ONVIFResponse(private val document: Document) {
    fun isFault(): Boolean {
        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            if (elements.item(i).nodeName.substringAfter(':') == "Fault") return true
        }
        return false
    }

    fun getProperty(name: String): String? {
        return try {
            val elements = document.getElementsByTagName(name)
            if (elements.length > 0) {
                elements.item(0).textContent
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getPropertyAsString(name: String): String? = getProperty(name)
    
    fun getAttribute(name: String): String? {
        return try {
            val element = document.documentElement
            val attr = element.getAttribute(name)
            attr.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }
    
    fun debugDumpProperties(): String {
        return try {
            val element = document.documentElement
            val children = element.childNodes
            val properties = mutableListOf<String>()
            
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    properties.add("${child.nodeName}: ${child.textContent}")
                }
            }
            
            "Available properties: ${properties.joinToString(", ")}"
        } catch (e: Exception) {
            "Error dumping properties: ${e.message}"
        }
    }
    
    fun getPropertyList(name: String): List<ONVIFResponse>? {
        return try {
            val elements = document.getElementsByTagName(name)
            val results = mutableListOf<ONVIFResponse>()
            
            for (i in 0 until elements.length) {
                val element = elements.item(i) as Element
                val newDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
                val importedNode = newDoc.importNode(element, true)
                newDoc.appendChild(importedNode)
                results.add(ONVIFResponse(newDoc))
            }

            results.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    override fun toString(): String {
        return try {
            val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            val source = javax.xml.transform.dom.DOMSource(document)
            val writer = StringWriter()
            val result = javax.xml.transform.stream.StreamResult(writer)
            transformer.transform(source, result)
            writer.toString()
        } catch (e: Exception) {
            ""
        }
    }
}
