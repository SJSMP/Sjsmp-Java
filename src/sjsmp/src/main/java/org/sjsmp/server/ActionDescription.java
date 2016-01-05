package org.sjsmp.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.sjsmp.DataTypes;

/**
 *
 * @author kondrashin_aa
 */
final class ActionDescription 
{
    public final String name;
    public final String desctiption;
    public final boolean requireConfirm;

    private final Method m_method;
    private final Map<String, MethodParameter> m_parameters = new HashMap<>();

    public ActionDescription(final String name, final String description, final boolean requireConfirm, final Method method)
    {
        this.name = name;
        this.desctiption = description;
        this.requireConfirm = requireConfirm;
        this.m_method = method;

        final Parameter[] parameters = this.m_method.getParameters();
        for (int i = 0; i < parameters.length; ++i)
        {
            final Parameter parameter = parameters[i];
            m_parameters.put(parameter.getName(), new MethodParameter(i, parameter.getType()));
        }
    }
    
    public Object Call(final Object obj, final JSONObject parameters) throws SjsmpServerException
    {
        final Object[] arguments = new Object[m_parameters.size()];
        for (final String name : parameters.keySet())
        {
            final Object value = parameters.get(name);
            final MethodParameter parameter = m_parameters.get(name);
            if (parameter == null)
            {
                throw new SjsmpServerException("Argument '" + name + "' not found in method '" + this.name + "'");
            }

            if (!parameter.type.equals(value.getClass()))
            {
                throw new SjsmpServerException("Argument '" + name + "' has wrong type");
            }

            arguments[parameter.index] = value;
        }

        final Object returnValue;
        try
        {
            returnValue = m_method.invoke(obj, arguments);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            throw new SjsmpServerException("Error calling '" + this.name + "'", e);
        }

        if (this.m_method.getReturnType() == void.class)
        {
            return "void";
        }

        return returnValue;
    }

    public JSONObject ToJObject()
    {
        final JSONObject result = new JSONObject();
        result.put("result", DataTypes.TypeToName(m_method.getReturnType()));
        result.put("description", this.desctiption);
        if (requireConfirm)
        {
        	result.put("require_confirm", this.requireConfirm);
        }

        final JSONObject parameters = new JSONObject();
        for (final Parameter pi : m_method.getParameters())
        {
            final JSONObject parameter = new JSONObject();
            parameter.put("type", DataTypes.TypeToName(pi.getType()));

            final SjsmpActionParameter attr = pi.getAnnotation(SjsmpActionParameter.class);
            if (attr != null)
            {
                parameter.put("description", attr.value());
            }
            parameters.put(pi.getName(), parameter);
        }
        result.put("parameters", parameters);
        return result;
    }

    private static final class MethodParameter
    {
        public final int index;
        public final Class<?> type;

        public MethodParameter(final int index, final Class<?> type)
        {
            this.index = index;
            this.type = type;
        }
    }
}
