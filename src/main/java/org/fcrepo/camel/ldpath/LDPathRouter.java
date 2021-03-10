/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.camel.ldpath;

import static java.util.Collections.singletonList;
import static org.apache.camel.builder.PredicateBuilder.and;
import static org.apache.camel.builder.PredicateBuilder.not;
import static org.apache.camel.model.dataformat.JsonLibrary.Jackson;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.HTTP_METHOD;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.marmotta.ldpath.LDPath;
import org.apache.marmotta.ldpath.backend.linkeddata.LDCacheBackend;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * A content router for an LDPath service.
 *
 * @author Aaron Coburn
 * @since Aug 5, 2016
 */
public class LDPathRouter extends RouteBuilder {

    private static final Logger LOGGER = getLogger(LDPathRouter.class);

    private static final String host = "0.0.0.0";
    private static final int port = 9086;
    private static final String prefix = "/ldpath";

    /**
     * Configure the message route workflow.
     */
    public void configure() throws Exception {

        /**
         * Expose a RESTful endpoint for LDPath processing
         */
        from("jetty:http://" + host + ":" + port + prefix + "?" +
                "&httpMethodRestrict=GET,POST,OPTIONS" +
                "&sendServerVersion=false")
            .routeId("FcrepoLDPathRest")
            .routeDescription("Expose the ldpath endpoint over HTTP")
                .log("Received http request to ldpath")
            .choice()
                .when(header(HTTP_METHOD).isEqualTo("OPTIONS"))
                    .log("Our header HTTP_METHOD is OPTIONS")
                    .setHeader(CONTENT_TYPE).constant("text/turtle")
                    .setHeader("Allow").constant("GET,POST,OPTIONS")
                    .to("language:simple:resource:classpath:org/fcrepo/camel/ldpath/options.ttl")
                // make sure the required context parameter is present
                .when(not(and(header("context").isNotNull(), header("context").regex("^https?://.+"))))
                    .log("Our CONTEXT is missing")
                    .setHeader(HTTP_RESPONSE_CODE).constant(400)
                    .setHeader(CONTENT_TYPE).constant("text/plain")
                    .transform(constant("Missing context parameter"))
                .when(header(HTTP_METHOD).isEqualTo("GET"))
                    .log("Our header HTTP_METHOD is GET")
                    .to("direct:get")
                .when(header(HTTP_METHOD).isEqualTo("POST"))
                .log("Our header HTTP_METHOD is POST")
                    .to("direct:ldpathPrepare");

        from("direct:get")
            .routeId("FcrepoLDPathGet")
            .choice()
                .when(and(header("ldpath").isNotNull(), header("ldpath").regex("^https?://.*")))
                    .log("We HAVE ldpath inside the headers")
                    .removeHeaders("CamelHttp*")
                    .setHeader(HTTP_URI).header("ldpath")
                    .to("http4://localhost?useSystemProperties=true")
                    .to("direct:ldpathPrepare")
                .otherwise()
                    .log("We DONT have ldpath inside headers")
                    .to("language:simple:resource:classpath:org/fcrepo/camel/ldpath/default.ldpath")
                    .to("direct:ldpathPrepare");

        from("direct:ldpathPrepare").routeId("FcrepoLDPathPrepare")
            .to("direct:ldpath")
            .to("direct:format");

        from("direct:ldpath")
                .routeId("FcrepoLDPath")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        InputStream body = exchange.getIn().getBody(InputStream.class);
                        //String someTest = new BufferedReader(new InputStreamReader(body)).lines().collect(Collectors.joining("\n"));
                        System.out.println("---------- WE ARE IN PROCESSOR ----------------");
                        //System.out.println(someTest);

                        Map<String, Object> headers = exchange.getIn().getHeaders();
                        // System.out.println(headers.get("context"));

                        final LDCacheBackend backend = new LDCacheBackend();
                        LDPath<Value> ldpath = new LDPath<Value>(backend);


                        exchange.getIn().setBody(singletonList(ldpath.programQuery(
                            new URIImpl((String) headers.get("context")), new InputStreamReader(body)
                        )));


                        // List<Fruit> test = new ArrayList<>();
                        // test.add(new Fruit(1, "Banana is yellow"));
                        // test.add(new Fruit(2, "Apple is green"));
                        // test.add(new Fruit(3, "Ananas i just ..."));
                        // exchange.getIn().setBody(test);
                    }
                }).end();

        from("direct:format").routeId("FcrepoLDPathFormat")
            .marshal().json(Jackson)
            .removeHeaders("*")
            .setHeader(CONTENT_TYPE).constant("application/json");

    }
}
