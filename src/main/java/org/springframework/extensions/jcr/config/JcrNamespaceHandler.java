/**
 * Copyright 2009 the original author or authors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.springframework.extensions.jcr.config;

import java.util.Iterator;
import java.util.List;

import javax.jcr.observation.Event;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.core.Constants;
import org.springframework.util.xml.DomUtils;
import org.springframework.extensions.jcr.EventListenerDefinition;
import org.springframework.extensions.jcr.JcrSessionFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * NamespaceHandler for Jcr tags.
 * 
 * @author Costin Leau
 * @author Sergio Bossa 
 * @author Salvatore Incandela
 * 
 */
public class JcrNamespaceHandler extends NamespaceHandlerSupport {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.springframework.beans.factory.xml.NamespaceHandler#init()
	 */
	public void init() {
		// registerBeanDefinitionParser("repository", new
		// JcrBeanDefinitionParser());
		registerBeanDefinitionParser("eventListenerDefinition",
				new JcrEventListenerBeanDefinitionParser());
		registerBeanDefinitionParser("sessionFactory",
				new JcrSessionFactoryBeanDefinitionParser());
	}

	private class JcrEventListenerBeanDefinitionParser extends
			AbstractSimpleBeanDefinitionParser {
		public static final String EVENT_TYPE = "eventType";

		public static final String NODE_TYPE_NAME = "nodeTypeName";

		public static final String UUID = "uuid";

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser
		 * #getBeanClass(org.w3c.dom.Element)
		 */
		protected Class getBeanClass(Element element) {
			return EventListenerDefinition.class;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser
		 * #postProcess(org.springframework.beans.factory.support.
		 * BeanDefinitionBuilder, org.w3c.dom.Element)
		 */
		protected void postProcess(BeanDefinitionBuilder definitionBuilder,
				Element element) {
			NodeList childNodes = element.getChildNodes();
			List eventTypes = DomUtils.getChildElementsByTagName(element,
					EVENT_TYPE);
			if (eventTypes != null && eventTypes.size() > 0) {
				// compute event type
				int eventType = 0;
				Constants types = new Constants(Event.class);
				for (Iterator iter = eventTypes.iterator(); iter.hasNext();) {
					Element evenTypeElement = (Element) iter.next();
					eventType |= types.asNumber(
							DomUtils.getTextValue(evenTypeElement)).intValue();
				}
				definitionBuilder.addPropertyValue(EVENT_TYPE, new Integer(
						eventType));
			}

			List nodeTypeNames = DomUtils.getChildElementsByTagName(element,
					NODE_TYPE_NAME);
			String[] nodeTypeValues = new String[nodeTypeNames.size()];

			for (int i = 0; i < nodeTypeValues.length; i++) {
				nodeTypeValues[i] = DomUtils
						.getTextValue((Element) nodeTypeNames.get(i));
			}
			definitionBuilder.addPropertyValue(NODE_TYPE_NAME, nodeTypeValues);
			List uuids = DomUtils.getChildElementsByTagName(element, UUID);

			String[] uuidsValues = new String[uuids.size()];

			for (int i = 0; i < uuidsValues.length; i++) {
				uuidsValues[i] = DomUtils.getTextValue((Element) uuids.get(i));
			}

			definitionBuilder.addPropertyValue(UUID, uuidsValues);
		}
	}

	private class JcrSessionFactoryBeanDefinitionParser extends
			AbstractSimpleBeanDefinitionParser {
		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser
		 * #getBeanClass(org.w3c.dom.Element)
		 */
		protected Class getBeanClass(Element element) {
			return JcrSessionFactory.class;
		}
	}
}
