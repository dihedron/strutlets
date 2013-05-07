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

package org.dihedron.strutlets.interceptors.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.dihedron.reflection.Reflector;
import org.dihedron.reflection.ReflectorException;
import org.dihedron.strutlets.ActionContext;
import org.dihedron.strutlets.ActionInvocation;
import org.dihedron.strutlets.actions.Action;
import org.dihedron.strutlets.annotations.In;
import org.dihedron.strutlets.annotations.Invocable;
import org.dihedron.strutlets.annotations.Scope;
import org.dihedron.strutlets.exceptions.InterceptorException;
import org.dihedron.strutlets.exceptions.StrutletsException;
import org.dihedron.strutlets.interceptors.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The interceptor devoted to handling of {@code @In} annotated fields: each 
 * input field for the given action method (as per the method's {@code inputs}
 * fields in the {@code @Invocable} annotation) is scanned, and the corresponding
 * value sought for in the available scopes.
 * 
 * @author Andrea Funto'
 */
public class Inputs extends Interceptor {

	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(Inputs.class);

	/**
	 * Looks for fields annotated with {@code @In} and injects them with values 
	 * picked from a set of available scopes. 
	 * 
	 * @param invocation
	 *   the current action invocation.
	 * @return
	 *   the result of the nested components' execution.
	 * @throws StrutletsException 
	 * @see 
	 *   org.dihedron.strutlets.interceptors.Interceptor#intercept(org.dihedron.strutlets.ActionInvocation)
	 */
	@Override
	public String intercept(ActionInvocation invocation) throws StrutletsException {
		logger.debug("injecting parameters into action");
		try {
			injectInputs(invocation);
			return invocation.invoke();
		} catch(ReflectorException e) {
			throw new InterceptorException("error setting input fields", e);
		}		
	}
	
	/**
	 * Looks up the {@code @In}-annotated fields and then invokes the value 
	 * injection method on each of them.
	 * 
	 * @param invocation
	 *   the current action invocation.
	 * @throws ReflectorException
	 *   if any error occurs while accessing the fields through reflection.
	 * @throws StrutletsException 
	 */
	private void injectInputs(ActionInvocation invocation) throws ReflectorException, StrutletsException {
		
		logger.trace("injecting inputs for method '{}' on action '{}'", invocation.getMethod(), invocation.getAction().getClass().getSimpleName());
		// get the method through reflection
		Set<Method> methods = Reflector.getMethods(invocation.getAction().getClass(), invocation.getMethod());
		if(methods.size() == 1) {
			// now get the input fields for the given method, as declared in the annotation
			String [] filter = {};
			Method method = (Method)methods.toArray()[0];
			if(method.isAnnotationPresent(Invocable.class)) {
				Invocable annotation = method.getAnnotation(Invocable.class);
				filter = annotation.inputs();
				// get the corresponding fields
				Set<Field> fields = Reflector.getFields(invocation.getAction().getClass(), filter);
				for(Field field : fields) {
					injectField(field, invocation);
				}		
			}
		}
	}
	
	/**
	 * Injects a single fields with the value picked from the available scopes,
	 * as per the {@code @In} annotation.
	 * 
	 * @param field
	 *   the field to inject.
	 * @param invocation
	 *   the current invocation object, from which the value will be picked.
	 * @throws ReflectorException
	 *   if any error occurs while accessing the action's field through reflection.
	 * @throws StrutletsException
	 *   if an unsupported  
	 */
	private void injectField(Field field, ActionInvocation invocation) throws ReflectorException, StrutletsException {
		logger.trace("looking up value of field '{}'", field.getName());
		if(field.isAnnotationPresent(In.class)) {
			
			In annotation = field.getAnnotation(In.class);
			
			// get the name of the parameter to look up; if none provided in the annotation,
			// take the name of the field itself			
			String parameter = annotation.value().length() > 0 ? annotation.value() : field.getName();
			
			// now, depending on the scope, try to locate the parameter in the appropriate context 
			Object value = null;
			for(Scope scope : annotation.scopes()) {
				logger.trace("scanning input scope '{}' for parameter '{}'...", scope.name(), parameter);
				if(scope == Scope.FORM) {
					value = ActionContext.getParameterValues(parameter);
					if(value != null) {
						logger.trace("... value for '{}' found in FORM parameters: '{}'", parameter, value);
						break;
					}
				} else if(scope == Scope.REQUEST) {
					value = ActionContext.getRequestAttribute(parameter);
					if(value != null) {
						logger.trace("... value for '{}' found in REQUEST attributes: '{}'", parameter, value);
						break;
					}
				} else if(scope == Scope.SESSION) {
					value = ActionContext.getSessionAttribute(parameter);
					if(value != null) {
						logger.trace("... value for '{}' found in SESSION attributes: '{}'", parameter, value);
						break;
					}
				} else if(scope == Scope.APPLICATION) {
					value = ActionContext.getApplicationAttribute(parameter);
					if(value != null) {
						logger.trace("... value for '{}' found in APPLICATION attributes: '{}'", parameter, value);
						break;
					}
				} else if(scope == Scope.CONFIGURATION) {
					value = invocation.getAction().getParameter(parameter);
					if(value != null) {
						logger.trace("... value for '{}' found in CONFIGURATION parameters: '{}'", parameter, value);
						break;
					}
				} else {
					logger.error("cannot extract an input value from the {} scope: this is probably a bug!", scope.name());
					throw new StrutletsException("Cannot extract an input value from a " + scope.name() + "scope: this is probably a bug!");					
				}
			}
			
			if(value != null) {
				Action action = invocation.getAction();
				if(field.getType() == value.getClass()) { 
					// if both are strings, or whatever, but equal, assign directly
					new Reflector(action).setFieldValue(field.getName(), value);
				} else if(field.getType().isArray() && value.getClass().isArray()){
					// both arrays, try to assign 
					new Reflector(action).setFieldValue(field.getName(), value);
				} else if(field.getType() == String.class && value.getClass().isArray()) {
					// pick just the first value
					new Reflector(action).setFieldValue(field.getName(), new Reflector(value).getElementAtIndex(0));
				}
			} else {
				logger.warn("no value found for '{}' in declared scopes", parameter);
			}			
		}
	}
}