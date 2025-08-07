package com.function;

import java.net.URI;
import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.credential.WebCredentials;

public class ServerConnect {

    public static ExchangeService connect() {
        try {
            ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);

            // Set your credentials here securely in production (env vars or key vault)
            String username = "malmutawa";
            String password = "M07ammed";
            String domain = "MOH";
            String url = "https://webmail.moh.gov.kw/EWS/Exchange.asmx";

            service.setCredentials(new WebCredentials(username, password, domain));
            service.setUrl(new URI(url));

            return service;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
