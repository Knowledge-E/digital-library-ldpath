package org.fcrepo.camel.ldpath;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

public class App
{
    public static void main( String[] args ) throws Exception
    {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new LDPathRouter());

        context.start();
    }
}
