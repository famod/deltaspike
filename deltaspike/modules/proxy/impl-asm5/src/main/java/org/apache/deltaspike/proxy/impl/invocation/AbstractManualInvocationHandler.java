/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.deltaspike.proxy.impl.invocation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.InterceptionType;
import javax.enterprise.inject.spi.Interceptor;
import javax.interceptor.InterceptorBinding;
import org.apache.deltaspike.core.api.provider.BeanManagerProvider;

@Typed
public abstract class AbstractManualInvocationHandler implements InvocationHandler
{
    @Override
    public Object invoke(Object proxy, Method method, Object[] parameters) throws Throwable
    {
        // check if interceptors are defined, otherwise just call the original logik
        List<Interceptor<?>> interceptors = resolveInterceptors(proxy, method);
        if (interceptors != null && interceptors.size() > 0)
        {
            try
            {
                ManualInvocationContext invocationContext =
                        new ManualInvocationContext(this, interceptors, proxy, method, parameters, null);

                Object returnValue = invocationContext.proceed();

                if (invocationContext.isProceedOriginal())
                {
                    return invocationContext.getProceedOriginalReturnValue();
                }

                return returnValue;
            }
            catch (ManualInvocationThrowableWrapperException e)
            {
                throw e.getCause();
            }
        }

        return proceedOriginal(proxy, method, parameters);
    }

    /**
     * Calls the original logic after invoking the interceptor chain.
     * 
     * @param proxy The current proxy instance.
     * @param method The current invoked method.
     * @param parameters The method parameter.
     * @return The original value from the original method.
     * @throws Throwable 
     */
    protected abstract Object proceedOriginal(Object proxy, Method method, Object[] parameters) throws Throwable;

    protected List<Interceptor<?>> resolveInterceptors(Object instance, Method method)
    {
        BeanManager beanManager = BeanManagerProvider.getInstance().getBeanManager();
        
        Annotation[] interceptorBindings = extractInterceptorBindings(beanManager, instance, method);
        if (interceptorBindings.length > 0)
        {
            return beanManager.resolveInterceptors(InterceptionType.AROUND_INVOKE, interceptorBindings);
        }

        return null;
    }

    protected Annotation[] extractInterceptorBindings(BeanManager beanManager, Object instance, Method method)
    {
        ArrayList<Annotation> bindings = new ArrayList<Annotation>();

        addInterceptorBindings(beanManager, bindings, instance.getClass().getDeclaredAnnotations());
        addInterceptorBindings(beanManager, bindings, method.getDeclaredAnnotations());

        return bindings.toArray(new Annotation[bindings.size()]);
    }
    
    protected void addInterceptorBindings(BeanManager beanManager, ArrayList<Annotation> bindings,
            Annotation[] declaredAnnotations)
    {
        for (Annotation annotation : declaredAnnotations)
        {
            if (bindings.contains(annotation))
            {
                continue;
            }
            
            Class<? extends Annotation> annotationType = annotation.annotationType();
            
            if (annotationType.isAnnotationPresent(InterceptorBinding.class))
            {
                bindings.add(annotation);
            }
            
            if (beanManager.isStereotype(annotationType))
            {
                for (Annotation subAnnotation : annotationType.getDeclaredAnnotations())
                {                    
                    if (bindings.contains(subAnnotation))
                    {
                        continue;
                    }

                    if (subAnnotation.annotationType().isAnnotationPresent(InterceptorBinding.class))
                    {
                        bindings.add(subAnnotation);
                    }  
                }
            }
        }
    }
}
