package com.stormacq.android.maxi80


import java.net.InetAddress
import java.net.Socket

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TLSSocketFactory : SSLSocketFactory()  {

    private var internalSSLSocketFactory : SSLSocketFactory

    init {
        val context = SSLContext.getInstance("TLS")
        context.init(null, null, null)
        internalSSLSocketFactory = context.socketFactory
    }


    override fun getDefaultCipherSuites() : Array<String> {
        return internalSSLSocketFactory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites() : Array<String> {
        return internalSSLSocketFactory.supportedCipherSuites
    }

    override fun  createSocket() : Socket?  {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket())
    }

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean) : Socket? {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(s, host, port, autoClose))
    }

    override fun  createSocket(host: String, port: Int) : Socket? {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port))
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) : Socket? {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress, port: Int) : Socket? {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port))
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int) : Socket? {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(address, port, localAddress, localPort))
    }

    private fun  enableTLSOnSocket(socket: Socket?) : Socket? {
        if(socket != null && (socket is SSLSocket)) {
            socket.enabledProtocols = arrayOf("TLSv1.1", "TLSv1.2")
        }
        return socket
    }
}