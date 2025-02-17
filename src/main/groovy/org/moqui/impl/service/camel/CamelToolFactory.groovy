/*
 * This software is in the public domain under CC0 1.0 Universal plus a 
 * Grant of Patent License.
 * 
 * To the extent possible under law, the author(s) have dedicated all
 * copyright and related and neighboring rights to this software to the
 * public domain worldwide. This software is distributed without any
 * warranty.
 * 
 * You should have received a copy of the CC0 Public Domain Dedication
 * along with this software (see the LICENSE.md file). If not, see
 * <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.moqui.impl.service.camel

import groovy.transform.CompileStatic
import org.apache.camel.CamelContext
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultPackageScanClassResolver
import org.apache.camel.spi.PackageScanClassResolver
import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** A ToolFactory for Apache Camel, an Enterprise Integration Patterns toolkit used for message processing and
 * integrated with the ServiceFacade with an end point to use services to produce and consume Camel messages. */
@CompileStatic
class CamelToolFactory implements ToolFactory<CamelContext> {
    protected final static Logger logger = LoggerFactory.getLogger(CamelToolFactory.class)
    final static String TOOL_NAME = "Camel"

    protected ExecutionContextFactory ecf = null
    /** The central object of the Camel API: CamelContext */
    protected CamelContext camelContext
    protected MoquiServiceComponent moquiServiceComponent
    protected Map<String, MoquiServiceConsumer> camelConsumerByUriMap = new HashMap<String, MoquiServiceConsumer>()

    /** Default empty constructor */
    CamelToolFactory() { }

    @Override
    String getName() { return TOOL_NAME }
    @Override
    void init(ExecutionContextFactory ecf) {
        logger.info("Starting Camel")
        moquiServiceComponent = new MoquiServiceComponent(this)
        camelContext.addComponent("moquiservice", moquiServiceComponent)
        if(System.getProperty("org.moqui.camel.routes.package"))loadRoutes()
        camelContext.start()
    }
    @Override
    void preFacadeInit(ExecutionContextFactory ecf) {
        this.ecf = ecf
        // setup the CamelContext, but don't init moquiservice Camel Component yet
        camelContext = new DefaultCamelContext()
    }

    @Override
    CamelContext getInstance(Object... parameters) { return camelContext }

    @Override
    void destroy() {
        // stop Camel to prevent more calls coming in
        if (camelContext != null) try {
            camelContext.stop()
            logger.info("Camel stopped")
        } catch (Throwable t) { logger.error("Error in Camel stop", t) }
    }

    ExecutionContextFactory getEcf() { return ecf }
    MoquiServiceComponent getMoquiServiceComponent() { return moquiServiceComponent }
    void registerCamelConsumer(String uri, MoquiServiceConsumer consumer) { camelConsumerByUriMap.put(uri, consumer) }
    MoquiServiceConsumer getCamelConsumer(String uri) { return camelConsumerByUriMap.get(uri) }


    /**
     * Auto load camel routes
     */
    private void loadRoutes(){
        logger.info("Loading routes...")
        PackageScanClassResolver packageResolver = new DefaultPackageScanClassResolver()
        Set<Class<?>> routesClassesSet = packageResolver.findImplementations(RouteBuilder.class, System.getProperty("org.moqui.camel.routes.package"));
        routesClassesSet.each{key ->
            RouteBuilder routeBuilder = createRoutes(key.getName());
            addRoutesToContext(routeBuilder);
        }
    }

    /**
     * Loads {@code routeBuilderCls} to add into CamelContext
     * @param routeBuilderCls
     * @return
     */
    private RouteBuilder createRoutes(String routeBuilderCls) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class<?> c = loader.loadClass(routeBuilderCls);
            return (RouteBuilder) c.newInstance();
        } catch (Exception e) {
            logger.error("Error in loading routeBuilderCls "+routeBuilderCls, e)
        }
    }

    /**
     * Adds {@code routeBuilder} to the CamelContext
     * @param routeBuilder
     */
    private void addRoutesToContext(RouteBuilder routeBuilder) {
        try {
            camelContext.addRoutes(routeBuilder);
        } catch (Exception e) {
            logger.error("Cannot add routes to : " + routeBuilder, e);
        }
    }
}
