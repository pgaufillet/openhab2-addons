/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.netatmo.internal;

import static org.openhab.binding.netatmo.NetatmoBindingConstants.*;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.http.HttpServlet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.netatmo.handler.NetatmoBridgeHandler;
import org.openhab.binding.netatmo.internal.discovery.NetatmoModuleDiscoveryService;
import org.openhab.binding.netatmo.internal.homecoach.NAHealthyHomeCoachHandler;
import org.openhab.binding.netatmo.internal.station.NAMainHandler;
import org.openhab.binding.netatmo.internal.station.NAModule1Handler;
import org.openhab.binding.netatmo.internal.station.NAModule2Handler;
import org.openhab.binding.netatmo.internal.station.NAModule3Handler;
import org.openhab.binding.netatmo.internal.station.NAModule4Handler;
import org.openhab.binding.netatmo.internal.thermostat.NAPlugHandler;
import org.openhab.binding.netatmo.internal.thermostat.NATherm1Handler;
import org.openhab.binding.netatmo.internal.welcome.NAWelcomeCameraHandler;
import org.openhab.binding.netatmo.internal.welcome.NAWelcomeHomeHandler;
import org.openhab.binding.netatmo.internal.welcome.NAWelcomePersonHandler;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NetatmoHandlerFactory} is responsible for creating things and
 * thing handlers.
 *
 * @author Gaël L'hopital - Initial contribution
 */

@Component(service = { ThingHandlerFactory.class,
        NetatmoHandlerFactory.class }, immediate = true, configurationPid = "binding.netatmo")
public class NetatmoHandlerFactory extends BaseThingHandlerFactory {
    private Logger logger = LoggerFactory.getLogger(NetatmoHandlerFactory.class);
    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();
    private Map<ThingUID, ServiceRegistration<?>> webHookServiceRegs = new HashMap<>();
    private HttpService httpService;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return (SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID));
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (thingTypeUID.equals(APIBRIDGE_THING_TYPE)) {
            WelcomeWebHookServlet servlet = registerWebHookServlet(thing.getUID());
            NetatmoBridgeHandler bridgeHandler = new NetatmoBridgeHandler((Bridge) thing, servlet);
            registerDeviceDiscoveryService(bridgeHandler);
            return bridgeHandler;
        } else if (thingTypeUID.equals(MODULE1_THING_TYPE)) {
            return new NAModule1Handler(thing);
        } else if (thingTypeUID.equals(MODULE2_THING_TYPE)) {
            return new NAModule2Handler(thing);
        } else if (thingTypeUID.equals(MODULE3_THING_TYPE)) {
            return new NAModule3Handler(thing);
        } else if (thingTypeUID.equals(MODULE4_THING_TYPE)) {
            return new NAModule4Handler(thing);
        } else if (thingTypeUID.equals(MAIN_THING_TYPE)) {
            return new NAMainHandler(thing);
        } else if (thingTypeUID.equals(HOMECOACH_THING_TYPE)) {
            return new NAHealthyHomeCoachHandler(thing);
        } else if (thingTypeUID.equals(PLUG_THING_TYPE)) {
            return new NAPlugHandler(thing);
        } else if (thingTypeUID.equals(THERM1_THING_TYPE)) {
            return new NATherm1Handler(thing);
        } else if (thingTypeUID.equals(WELCOME_HOME_THING_TYPE)) {
            return new NAWelcomeHomeHandler(thing);
        } else if (thingTypeUID.equals(WELCOME_CAMERA_THING_TYPE)) {
            return new NAWelcomeCameraHandler(thing);
        } else if (thingTypeUID.equals(WELCOME_PERSON_THING_TYPE)) {
            return new NAWelcomePersonHandler(thing);
        } else {
            logger.warn("ThingHandler not found for {}", thing.getThingTypeUID());
            return null;
        }
    }

    @Override
    protected void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof NetatmoBridgeHandler) {
            ThingUID thingUID = thingHandler.getThing().getUID();
            unregisterDeviceDiscoveryService(thingUID);
            unregisterWebHookServlet(thingUID);
        }
        super.removeHandler(thingHandler);
    }

    private void registerDeviceDiscoveryService(@NonNull NetatmoBridgeHandler netatmoBridgeHandler) {
        if (bundleContext != null) {
            NetatmoModuleDiscoveryService discoveryService = new NetatmoModuleDiscoveryService(netatmoBridgeHandler);
            discoveryServiceRegs.put(netatmoBridgeHandler.getThing().getUID(), bundleContext.registerService(
                    DiscoveryService.class.getName(), discoveryService, new Hashtable<String, Object>()));
        }
    }

    private void unregisterDeviceDiscoveryService(ThingUID thingUID) {
        ServiceRegistration<?> serviceReg = discoveryServiceRegs.get(thingUID);
        if (serviceReg != null) {
            serviceReg.unregister();
            discoveryServiceRegs.remove(thingUID);
        }
    }

    private WelcomeWebHookServlet registerWebHookServlet(ThingUID thingUID) {
        WelcomeWebHookServlet servlet = null;
        if (bundleContext != null) {
            servlet = new WelcomeWebHookServlet(httpService, thingUID.getId());
            webHookServiceRegs.put(thingUID, bundleContext.registerService(HttpServlet.class.getName(), servlet,
                    new Hashtable<String, Object>()));
        }
        return servlet;
    }

    private void unregisterWebHookServlet(ThingUID thingUID) {
        ServiceRegistration<?> serviceReg = webHookServiceRegs.get(thingUID);
        if (serviceReg != null) {
            serviceReg.unregister();
            webHookServiceRegs.remove(thingUID);
        }
    }

    @Reference
    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    public void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }

}
