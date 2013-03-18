/*
 * Copyright (C) 2003 Sun Microsystems, Inc.
 * Copyright (C) 2003-2010 Martin Koegler
 * Copyright (C) 2006 OCCAM Financial Technology
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
 * USA.
 */

package android.androidVNC;

import java.util.*;
import java.net.*;
import javax.net.ssl.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.io.*;

public class X509Tunnel extends TLSTunnelBase
{

  Certificate cert;

  public X509Tunnel (Socket sock_, String certstr) throws CertificateException
  {
    super (sock_);

    if (certstr != null)
    {
      CertificateFactory cf = CertificateFactory.getInstance ("X.509");
      cert = cf.generateCertificate (new StringBufferInputStream (certstr));
    }
  }

  protected void setParam (SSLSocket sock)
  {
    String[] supported;
    ArrayList enabled = new ArrayList ();

    supported = sock.getSupportedCipherSuites ();

    for (int i = 0; i < supported.length; i++)
      if (!supported[i].matches (".*DH_anon.*"))
        enabled.add (supported[i]);

    sock.setEnabledCipherSuites ((String[]) enabled.toArray (new String[0]));
  }

  protected void initContext (SSLContext sc)
      throws java.security.GeneralSecurityException
  {
    TrustManager[] myTM;

    if (cert != null)
    {
      myTM = new TrustManager[]
      { new X509TrustManager ()
      {
        public java.security.cert.X509Certificate[] getAcceptedIssuers ()
        {
          return null;
        }

        public void checkClientTrusted (
            java.security.cert.X509Certificate[] certs, String authType)
            throws CertificateException
        {
          throw new CertificateException ("no clients");
        }

        public void checkServerTrusted (
            java.security.cert.X509Certificate[] certs, String authType)
            throws CertificateException
        {

          if (certs == null || certs.length < 1)
          {
            throw new CertificateException ("no certs");
          }
          if (certs == null || certs.length > 1)
          {
            throw new CertificateException ("cert path too long");
          }
          PublicKey cakey = cert.getPublicKey ();

          boolean ca_match;
          try
          {
            certs[0].verify (cakey);
            ca_match = true;
          } catch (Exception e)
          {
            ca_match = false;
          }

          if (!ca_match && !cert.equals (certs[0]))
          {
            throw new CertificateException ("certificate does not match");
          }
        }
      } };
    } else
    {
      TrustManagerFactory tmf = TrustManagerFactory.getInstance ("X509");
      KeyStore ks = KeyStore.getInstance (KeyStore.getDefaultType ());
      tmf.init (ks);
      myTM = tmf.getTrustManagers();
    }
    sc.init (null, myTM, null);
  }
}
