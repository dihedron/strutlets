/**
 * Copyright (c) 2012, 2013, Andrea Funto'. All rights reserved.
 * 
 * This file is part of the Strutlets framework ("Strutlets").
 *
 * Strutlets is free software: you can redistribute it and/or modify it under 
 * the terms of the GNU Lesser General Public License as published by the Free 
 * Software Foundation, either version 3 of the License, or (at your option) 
 * any later version.
 *
 * Strutlets is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR 
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more 
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License 
 * along with Strutlets. If not, see <http://www.gnu.org/licenses/>.
 */

package org.dihedron.strutlets.renderers.registry;

import java.util.Set;

import org.dihedron.strutlets.annotations.Alias;
import org.dihedron.strutlets.renderers.Renderer;
import org.dihedron.utils.Strings;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Funto'
 */
public class RendererRegistryLoader {
	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(RendererRegistryLoader.class);
	
	/**
     * This method performs the automatic scanning of renderers at startup time, 
     * to get any custom, user-provided renderers.
     */
    public void loadFromJavaPackage(RendererRegistry registry, String javaPackage) {
    	
    	if(Strings.isValid(javaPackage)) {
    		logger.trace("looking for renderer classes in package '{}'", javaPackage);

    		// use this approach because it seems to be consistently faster
    		// than the much simpler new Reflections(javaPackage) 
    		Reflections reflections = 
    				new Reflections(new ConfigurationBuilder()
    					.filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(javaPackage)))
    					.setUrls(ClasspathHelper.forPackage(javaPackage))
    					.setScanners(new SubTypesScanner()));    		
    		Set<Class<? extends Renderer>> renderers = reflections.getSubTypesOf(Renderer.class);
	        for(Class<?> clazz : renderers) {
	        	logger.trace("analysing renderer class: '{}'...", clazz.getName());
	        	if(clazz.isAnnotationPresent(Alias.class)) {
	        		Alias alias = clazz.getAnnotation(Alias.class);
	        		logger.trace("... registering '{}' renderer: '{}'", alias.value(), clazz.getCanonicalName());
	        		registry.put(alias.value(), clazz.getName());
	        	} else {
	        		logger.trace("... skipping renderer '{}'", clazz.getCanonicalName());
	        	}
	        }
    	}
    }		
}