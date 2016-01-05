package org.sjsmp.server;		

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

/**
 *
 * @author kondrashin_aa
 */
final class ObjectDescription
{
    public final String name;
    private final String description;
    private final String group;
    public final Map<String, PropertyDescription> properties = new HashMap<>();
    public final Map<String, ActionDescription> actions = new HashMap<>();

    public ObjectDescription(final Object obj, final String name, final String description, final String group) throws SjmpServerException
    {
        this.name = name;
        this.description = description;
        this.group = group;
        
        Map<String, Method> methods = new HashMap<>();        
        for (Method method : obj.getClass().getDeclaredMethods())
        {
            methods.put(method.getName(), method);
        }
        
        for (Map.Entry<String, Method> pair : methods.entrySet())
        {
            final String methodName = pair.getKey();
            final Method method = pair.getValue();
            
            final SjmpProperty property = method.getAnnotation(SjmpProperty.class);
            if (property != null && methodName.startsWith("get"))
            {
                if (method.getParameterCount() != 0)
                {
                    continue;
                }
                
                final Class<?> propertyType = method.getReturnType();
                final String methodBaseName = methodName.substring("get".length());
                
                Method methodSetter = null;
                if (!property.readonly())
                {
                    final String setterMethodName = "set" + methodBaseName;
                    if ((methodSetter = methods.get(setterMethodName)) != null)
                    {
                        final Class<?>[] setterParameters = methodSetter.getParameterTypes();
                        if (setterParameters.length != 1)
                        {
                            methodSetter = null;
                        }
                        else if (!setterParameters[0].equals(propertyType))
                        {
                            methodSetter = null;                            
                        }
                        else if (!methodSetter.getReturnType().equals(void.class))
                        {
                            methodSetter = null;
                        }
                    }                    
                }

                method.setAccessible(true);
                if (methodSetter != null)
                {
                    methodSetter.setAccessible(true);
                }
                
                boolean showGraph = property.showGraph(); 

                final PropertyDescription foundProperty = new PropertyDescription(methodBaseName, property.value(), propertyType, method, methodSetter, showGraph);
                properties.put(methodBaseName, foundProperty);
                continue;
            }
            
            final SjmpAction action = method.getAnnotation(SjmpAction.class);
            if (action != null)
            {
                method.setAccessible(true);
                final ActionDescription actionDescription = new ActionDescription(methodName, action.value(), action.requireConfirm(), method);
                actions.put(methodName, actionDescription);
                continue;
            }
        }
    }

    public JSONObject ToJSONObject()
    {
        final JSONObject result = new JSONObject();
        result.put("description", this.description);
        result.put("group", this.group);

        final JSONObject properties = new JSONObject();
        for (final PropertyDescription pd : this.properties.values())
        {
            properties.put(pd.name, pd.ToJObject());
        }
        result.put("properties", properties);

        final JSONObject actions = new JSONObject();
        for (final ActionDescription ad : this.actions.values())
        {
            actions.put(ad.name, ad.ToJObject());
        }
        result.put("actions", actions);
        return result;
    }
}
