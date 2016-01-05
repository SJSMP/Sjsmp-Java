package org.sjsmp.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.json.JSONObject;
import org.sjsmp.DataTypes;

/**
 *
 * @author kondrashin_aa
 */
final class PropertyDescription 
{
    public final String name;
    private final String description;
    private final Class<?> propertyType;
    private final Method m_getter;
    private final Method m_setter;
    private final boolean m_showGraph;
    private final String m_sjmpTypeName;
    private final boolean m_needToString;
    private final SjmpPropertyLimits m_limits;
    
    public PropertyDescription(final String baseName, final String description, final Class<?> propertyType, final Method getter, final Method setter, final boolean showGraph) throws SjmpServerException
    {
        this.name = baseName;
        this.description = description;
        this.m_getter = getter;
        this.m_showGraph = showGraph;
        this.propertyType = propertyType;
        String sjmpTypeName = DataTypes.TypeToNameOrNull(propertyType);
        if (sjmpTypeName == null)
        {
            m_sjmpTypeName = DataTypes.TypeToName(String.class);
            this.m_needToString = true;
            this.m_setter = null;
        }
        else
        {
        	this.m_sjmpTypeName = sjmpTypeName;
        	this.m_needToString = false;
            this.m_setter = setter;
        }
        
        if (m_showGraph && !DataTypes.IsGraphAllowed(m_sjmpTypeName))
        {
            throw new SjmpServerException("Having 'showGraph' for type '" + m_sjmpTypeName + "' is not allowed. " + getter.getDeclaringClass() + "." + getter.getName());
        }

        m_limits = getter.getAnnotation(SjmpPropertyLimits.class);
        if (m_limits != null)
        {
        	if (!DataTypes.IsIntType(propertyType) && !DataTypes.IsFloatType(propertyType))
            {
                throw new SjmpServerException("Having PropertyLimits for type '" + m_sjmpTypeName + "' is not allowed." + getter.getDeclaringClass() + "." + getter.getName());
            }
        }
        
        assert(this.m_getter != null);
    }
    
    public Object GetValue(final Object obj) throws SjmpServerException
    {
        Object value;
        try
        {
            value = m_getter.invoke(obj);
            if (m_needToString && value != null)
            {
            	value = value.toString();
            }
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            throw new SjmpServerException("Exception raised while calling getter '" + this.name + "' on object '" + obj + "'", e);
        }

        return value;
    }

    public void SetValue(final Object obj, final Object value) throws SjmpServerException
    {
        if (m_setter == null)
        {
            throw new SjmpServerException("Object '" + obj + "' does not have setter defined for property '" + this.name + "'");
        }

        if (m_limits != null)
        {
        	if (!(value instanceof Number))
        	{
        		throw new SjmpServerException("Bad value type " + value.getClass());
        	}
        		
        	double dVal = ((Number)value).doubleValue();
        		
        	if (dVal < m_limits.min())
        	{
        		throw new SjmpServerException("Trying to set a value that is less than minimal");
        	}
        	if (dVal > m_limits.max())
        	{
                throw new SjmpServerException("Trying to set a value that is greater than maximal");
        	}
        }

        try
        {
            m_setter.invoke(obj, value);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            throw new SjmpServerException("Exception raised while calling setter '" + this.name + "' on object '" + obj + "'", e);
        }
    }

    public JSONObject ToJObject()
    {
        final boolean isReadonly = m_setter == null;
        final JSONObject result = new JSONObject();
        final String friendlyTypeName = DataTypes.TypeToName(this.propertyType);
        result.put("type", friendlyTypeName);
        result.put("readonly", isReadonly);
        result.put("description", this.description);
        if (m_showGraph)
        {
        	result.put("show_graph", true);
        }
        if (m_limits != null)
        {
        	final JSONObject limitsObj = new JSONObject();
        	limitsObj.put("min", m_limits.min());
        	limitsObj.put("max", m_limits.max());
            result.put("limits", limitsObj);
        }
        return result;
    }
}
