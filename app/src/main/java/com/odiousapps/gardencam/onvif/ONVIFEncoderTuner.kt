package com.odiousapps.gardencam.onvif

import android.util.Log
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.net.URI

/**
 * Reconfigures every video encoder on the camera for the lowest latency ONVIF can ask for:
 * H.264 Baseline profile (which has no B-frames) and, through Media2, constant bitrate.
 * Vendor "smart" codecs (H.264+, Smart Codec, ZeroGOP) are outside ONVIF and are left alone.
 *
 * Each configuration is fetched, edited in place and sent back, so vendor fields survive,
 * then read back to report what the camera actually kept.
 */
class ONVIFEncoderTuner(
    private val deviceUrl: String,
    private val credentials: ONVIFCredentials?
) {
    companion object {
        private const val TAG = "ONVIF_TUNE"
        private const val DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl"
        private const val MEDIA_NS = "http://www.onvif.org/ver10/media/wsdl"
        private const val MEDIA2_NS = "http://www.onvif.org/ver20/media/wsdl"
        private const val SCHEMA_NS = ONVIFSoapClient.SCHEMA_NAMESPACE
        private const val BASELINE = "Baseline"
    }

    // A change made to a configuration, and how to confirm it on the read-back copy
    private class Change(val what: String, val isApplied: (Element) -> Boolean)

    /** Blocking network calls: run off the main thread. Returns one line per encoder. */
    fun applyLowLatency(): List<String> {
        val services = getServices()
        services[MEDIA2_NS]?.let { url ->
            tuneMedia2(ONVIFSoapClient(url, credentials))?.let { return it }
        }
        val mediaUrl = services[MEDIA_NS] ?: deviceUrl.replace("/device_service", "/Media")
        return tuneMedia1(ONVIFSoapClient(mediaUrl, credentials))
            ?: listOf("The camera did not return any video encoder settings.")
    }

    private fun getServices(): Map<String, String> {
        val response = ONVIFSoapClient(deviceUrl, credentials)
            .sendXmlRequest("GetServices", DEVICE_NS, "<tds:IncludeCapability>false</tds:IncludeCapability>")
            ?: return emptyMap()
        return response.elements(DEVICE_NS, "Service").mapNotNull { service ->
            val ns = service.childText(DEVICE_NS, "Namespace") ?: return@mapNotNull null
            val xAddr = service.childText(DEVICE_NS, "XAddr") ?: return@mapNotNull null
            ns to onDeviceHost(xAddr)
        }.toMap()
    }

    // Cameras often advertise an internal address; keep the path but use the host we reached
    private fun onDeviceHost(xAddr: String): String {
        return try {
            val base = URI(deviceUrl)
            val path = URI(xAddr.trim())
            URI(base.scheme, null, base.host, base.port, path.path, path.query, null).toString()
        } catch (_: Exception) {
            xAddr.trim()
        }
    }

    private fun tuneMedia2(client: ONVIFSoapClient): List<String>? {
        val configs = client.sendXmlRequest("GetVideoEncoderConfigurations", MEDIA2_NS, "")
            ?.elements(MEDIA2_NS, "Configurations").orEmpty()
        if (configs.isEmpty())
            return null

        return configs.map { config ->
            val token = config.getAttribute("token")
            val label = describe(config)
            val encoding = config.childText(SCHEMA_NS, "Encoding")
            val options = client.sendXmlRequest("GetVideoEncoderConfigurationOptions", MEDIA2_NS, tokenXml(token))
                ?.elements(MEDIA2_NS, "Options")
                ?.firstOrNull { it.childText(SCHEMA_NS, "Encoding") == encoding }
            val notes = mutableListOf<String>()
            val changes = mutableListOf<Change>()

            if (encoding == "H264") {
                val profiles = options?.getAttribute("ProfilesSupported").orEmpty().split(' ').filter { it.isNotEmpty() }
                when {
                    config.getAttribute("Profile") == BASELINE -> notes += "already Baseline"
                    BASELINE in profiles -> {
                        config.setAttribute("Profile", BASELINE)
                        changes += Change("Baseline profile") { it.getAttribute("Profile") == BASELINE }
                    }
                    else -> notes += notOffered(profiles)
                }
            } else {
                notes += noBaseline(encoding)
            }

            val rateControl = config.child(SCHEMA_NS, "RateControl")
            when {
                rateControl == null -> notes += "no bitrate settings"
                rateControl.getAttribute("ConstantBitRate") == "true" -> notes += "already CBR"
                options?.getAttribute("ConstantBitRateSupported") == "true" -> {
                    rateControl.setAttribute("ConstantBitRate", "true")
                    changes += Change("CBR") {
                        it.child(SCHEMA_NS, "RateControl")?.getAttribute("ConstantBitRate") == "true"
                    }
                }
                else -> notes += "CBR not supported"
            }

            if (changes.isNotEmpty()) {
                val body = toXml(config, MEDIA2_NS)
                notes += setAndVerify(client, MEDIA2_NS, body, changes) {
                    client.sendXmlRequest("GetVideoEncoderConfigurations", MEDIA2_NS, tokenXml(token))
                        ?.elements(MEDIA2_NS, "Configurations")?.firstOrNull()
                }
            }
            "$label: ${notes.joinToString(", ")}"
        }
    }

    // The original Media service has no CBR switch, so only the H.264 profile can be changed
    private fun tuneMedia1(client: ONVIFSoapClient): List<String>? {
        val configs = client.sendXmlRequest("GetVideoEncoderConfigurations", MEDIA_NS, "")
            ?.elements(MEDIA_NS, "Configurations").orEmpty()
        if (configs.isEmpty())
            return null

        return configs.map { config ->
            val token = config.getAttribute("token")
            val label = describe(config)
            val encoding = config.childText(SCHEMA_NS, "Encoding")
            val notes = mutableListOf<String>()
            val changes = mutableListOf<Change>()
            val profile = config.child(SCHEMA_NS, "H264")?.child(SCHEMA_NS, "H264Profile")

            if (encoding != "H264" || profile == null) {
                notes += noBaseline(encoding)
            } else if (profile.textContent.trim() == BASELINE) {
                notes += "already Baseline"
            } else {
                val profiles = client.sendXmlRequest("GetVideoEncoderConfigurationOptions", MEDIA_NS, tokenXml(token))
                    ?.elements(MEDIA_NS, "Options")?.firstOrNull()
                    ?.child(SCHEMA_NS, "H264")?.children(SCHEMA_NS, "H264ProfilesSupported")
                    ?.map { it.textContent.trim() }.orEmpty()
                if (BASELINE in profiles) {
                    profile.textContent = BASELINE
                    changes += Change("Baseline profile") {
                        it.child(SCHEMA_NS, "H264")?.childText(SCHEMA_NS, "H264Profile") == BASELINE
                    }
                } else {
                    notes += notOffered(profiles)
                }
            }
            notes += "CBR needs Media2, which this camera lacks"

            if (changes.isNotEmpty()) {
                val body = toXml(config, MEDIA_NS) +
                        "<tds:ForcePersistence>true</tds:ForcePersistence>"
                notes += setAndVerify(client, MEDIA_NS, body, changes) {
                    client.sendXmlRequest("GetVideoEncoderConfiguration", MEDIA_NS, tokenXml(token))
                        ?.elements(MEDIA_NS, "Configuration")?.firstOrNull()
                }
            }
            "$label: ${notes.joinToString(", ")}"
        }
    }

    private fun setAndVerify(
        client: ONVIFSoapClient,
        ns: String,
        body: String,
        changes: List<Change>,
        readBack: () -> Element?
    ): List<String> {
        if (client.sendXmlRequest("SetVideoEncoderConfiguration", ns, body) == null) {
            Log.w(TAG, "SetVideoEncoderConfiguration rejected")
            return listOf("camera rejected the change")
        }
        val current = readBack()
        return changes.map { change ->
            if (current != null && change.isApplied(current)) "${change.what} set"
            else "${change.what} ignored by camera"
        }
    }

    private fun describe(config: Element): String {
        val name = config.childText(SCHEMA_NS, "Name") ?: config.getAttribute("token")
        val resolution = config.child(SCHEMA_NS, "Resolution")
        val size = resolution?.let {
            " ${it.childText(SCHEMA_NS, "Width")}x${it.childText(SCHEMA_NS, "Height")}"
        } ?: ""
        return "$name (${config.childText(SCHEMA_NS, "Encoding") ?: "?"}$size)"
    }

    private fun notOffered(profiles: List<String>): String {
        return if (profiles.isEmpty()) "Baseline not offered"
        else "Baseline not offered (only ${profiles.joinToString("/")})"
    }

    private fun noBaseline(encoding: String?): String {
        return "${encoding ?: "unknown codec"}: B-frames can only be turned off via ONVIF for H.264"
    }

    private fun tokenXml(token: String): String {
        return "<tds:ConfigurationToken>${escape(token)}</tds:ConfigurationToken>"
    }

    // Writes a configuration from a Get...Configurations response back out as the Set request's
    // Configuration element (the response calls it Configurations)
    private fun toXml(config: Element, requestNs: String): String {
        return buildString { appendNode(config, requestNs, "Configuration") }
    }

    private fun StringBuilder.appendNode(node: Node, requestNs: String, nameOverride: String? = null) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> append(escape(node.nodeValue))
            Node.ELEMENT_NODE -> {
                val element = node as Element
                val localName = nameOverride ?: element.localName ?: element.tagName
                // The envelope declares "tds" (request namespace) and "tt" (schema); anything else
                // is a vendor extension and declares its own namespace
                val (name, declaration) = when (element.namespaceURI) {
                    requestNs -> "tds:$localName" to ""
                    SCHEMA_NS -> "tt:$localName" to ""
                    null -> localName to ""
                    else -> "x:$localName" to " xmlns:x=\"${escape(element.namespaceURI)}\""
                }
                append('<').append(name).append(declaration)
                val attributes = element.attributes
                for (i in 0 until attributes.length) {
                    val attribute = attributes.item(i)
                    // Skip namespace declarations and qualified attributes such as xsi:type
                    if (attribute.nodeName == "xmlns" || attribute.namespaceURI != null)
                        continue
                    append(' ').append(attribute.localName ?: attribute.nodeName)
                        .append("=\"").append(escape(attribute.nodeValue)).append('"')
                }
                append('>')
                val children = element.childNodes
                for (i in 0 until children.length)
                    appendNode(children.item(i), requestNs)
                append("</").append(name).append('>')
            }
        }
    }

    private fun escape(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun Document.elements(ns: String, name: String): List<Element> {
        val nodes = getElementsByTagNameNS(ns, name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.children(ns: String, name: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).map { nodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.namespaceURI == ns && it.localName == name }
    }

    private fun Element.child(ns: String, name: String): Element? = children(ns, name).firstOrNull()

    private fun Element.childText(ns: String, name: String): String? = child(ns, name)?.textContent?.trim()
}
