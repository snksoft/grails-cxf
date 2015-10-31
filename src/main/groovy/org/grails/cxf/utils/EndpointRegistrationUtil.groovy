package org.grails.cxf.utils

import groovy.util.logging.Slf4j
import org.apache.commons.lang3.StringUtils
import org.apache.cxf.Bus
import org.apache.cxf.interceptor.Interceptor
import org.apache.cxf.jaxws.EndpointImpl
import org.apache.cxf.message.Message
import org.grails.core.artefact.ServiceArtefactHandler
import org.springframework.aop.framework.Advised
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext

import javax.xml.ws.soap.SOAPBinding

@Slf4j
class EndpointRegistrationUtil {

	public static void wireEndpoints(ApplicationContext context) {

		Bus bus = (Bus) context.getBean(Bus.DEFAULT_BUS_ID)

		Map<String, Object> beansWithAnnotation = context.getBeansWithAnnotation(GrailsCxfEndpoint.class)
		for (Map.Entry<String, Object> entry : beansWithAnnotation.entrySet()) {
			Object implementor = entry.getValue()
			EndpointImpl endpoint = new EndpointImpl(bus, implementor)
			publishEndpointUrl(endpoint, implementor)
			addConfiguredProperties(endpoint, implementor, context)
		}
	}

	private static void publishEndpointUrl(EndpointImpl endpoint, Object implementor) {
		String url = getPublishUrl(implementor)
		log.info('Endpoint [' + implementor.class + '] configured to use url ' + url + '.');
		endpoint.publish(url)
	}

	private static void addConfiguredProperties(EndpointImpl endpoint, Object implementor, ApplicationContext context) {
		assert implementor != null
		GrailsCxfEndpoint annotation = null
		//This is done if you are using Hystrix or AOP on your service classes
		if (implementor instanceof Advised) {
			try {
				annotation = ((Advised) implementor).getTargetSource().getTarget().getClass().getAnnotation(GrailsCxfEndpoint.class)
			} catch (Exception e) {
				log.error('Could not wire AOP Proxied endpoint.', e)
			}
		} else {
			annotation = implementor.getClass().getAnnotation(GrailsCxfEndpoint.class)
		}
		if (annotation != null) {
			addSoap12(annotation, endpoint)
			addWsdl(annotation, endpoint)
			addProperties(annotation, implementor, endpoint)
			addInInterceptors(annotation, endpoint, context)
			addOutInterceptors(annotation, endpoint, context)
			addOutFaultInterceptors(annotation, endpoint, context)
		}
	}

	private static void addWsdl(GrailsCxfEndpoint annotation, EndpointImpl endpoint) {
		if (annotation.wsdl()) {
			endpoint.getServerFactory().wsdlLocation = annotation.wsdl()
		}
	}

	private static void addSoap12(GrailsCxfEndpoint annotation, EndpointImpl endpoint) {
		if (annotation.soap12()) {
			endpoint.getServerFactory().bindingId = SOAPBinding.SOAP12HTTP_MTOM_BINDING
		}
	}

	private
	static void addInInterceptors(GrailsCxfEndpoint annotation, EndpointImpl endpoint, ApplicationContext context) {
		try {
			for (String inInterceptorName : annotation.inInterceptors()) {
				endpoint.getServer().getEndpoint().getInInterceptors().add((Interceptor<? extends Message>) context.getBean(inInterceptorName))
				log.info('Endpoint [' + endpoint.address + '] configured to use in interceptor bean ' + inInterceptorName + '.');
			}
		} catch (BeansException e) {
			log.error('Could not wire in interceptors', e)
		}
	}

	private
	static void addOutInterceptors(GrailsCxfEndpoint annotation, EndpointImpl endpoint, ApplicationContext context) {
		try {
			for (String outInterceptorName : annotation.outInterceptors()) {
				endpoint.getServer().getEndpoint().getOutInterceptors().add((Interceptor<? extends Message>) context.getBean(outInterceptorName))
				log.info('Endpoint [' + endpoint.address + '] configured to use out interceptor bean ' + outInterceptorName + '.');
			}
		} catch (BeansException e) {
			log.error('Could not wire out interceptors', e)
		}
	}

	private
	static void addOutFaultInterceptors(GrailsCxfEndpoint annotation, EndpointImpl endpoint, ApplicationContext context) {
		try {
			for (String outFaultInterceptorName : annotation.outFaultInterceptors()) {
				endpoint.getServer().getEndpoint().getOutFaultInterceptors().add((Interceptor<? extends Message>) context.getBean(outFaultInterceptorName))
				log.info('Endpoint [' + endpoint.address + '] configured to use out fault interceptor bean ' + outFaultInterceptorName + '.');
			}
		} catch (BeansException e) {
			log.error('Could not wire out fault interceptors', e)
		}
	}

	private static void addProperties(GrailsCxfEndpoint annotation, implementor, EndpointImpl endpoint) {
		if (annotation?.properties()?.length > 0) {
			Map<String, String> properties = [:]
			for (GrailsCxfEndpointProperty prop : annotation.properties()) {
				properties.put(prop.name(), prop.value());
			}
			if (properties) {
				log.info('Endpoint [' + implementor.class + '] configured to use properties ' + properties + '.');
				endpoint.getServerFactory().properties = properties
			}
		}
	}

	private static String getPublishUrl(Object implementor) {
		assert implementor != null
		String url = ''
		if (implementor instanceof Advised) {
			try {
				GrailsCxfEndpoint annotation = ((Advised) implementor).getTargetSource().getTarget().getClass().getAnnotation(GrailsCxfEndpoint.class)
				if (annotation != null) {
					url = annotation.address()
				}
			} catch (Exception e) {
				log.error('Could not wire AOP Proxied smart api endpoint.', e)
			}
		} else {
			url = implementor.getClass().getAnnotation(GrailsCxfEndpoint.class)?.address()
		}

		url = url ?: getNameNoPostfix(implementor)
		return !url.startsWith('/') ? '/' + url : url
	}

	private static String getNameNoPostfix(Object endpoint) {
		String className = endpoint?.class?.simpleName
		String url = 'devnull'
		if (className?.endsWith(ServiceArtefactHandler.TYPE)) {
			url = StringUtils.removeEnd(className, ServiceArtefactHandler.TYPE)
		}
		return '/' + url.toLowerCase()
	}
}
