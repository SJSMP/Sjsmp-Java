package org.sjsmp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by kondrashin_aa on 24.08.2015.
 */
public final class DataTypes
{
    private static final Map<String, Class<?>> s_typeNames = new HashMap<>();
    private static final Map<Class<?>, String> s_types = new HashMap<>();
    private static final Map<String, Boolean> s_allowShowGraphForName = new HashMap<>();
    private static final Set<Class<?>> s_intTypes = new HashSet<>();
    private static final Set<Class<?>> s_floatTypes = new HashSet<>();

    static
    {
        AddType("void", 	void.class,		Void.class, 	false, 	null);
        AddType("string", 	null, 			String.class, 	false, 	null);
        AddType("float", 	float.class,	Float.class,	true,	s_floatTypes);
        AddType("double",	double.class,	Double.class,	true,	s_floatTypes);
        AddType("int32",	int.class,		Integer.class,	true,	s_intTypes);
        AddType("int64", 	long.class,		Long.class,		true,	s_intTypes);
        AddType("int16", 	short.class, 	Short.class,	true,	s_intTypes);
        AddType("int8",		byte.class,		Byte.class,		true,	s_intTypes);
        AddType("bool", 	boolean.class, 	Boolean.class,	false,	null);
    }

    private static void AddType(final String name, final Class<?> simpleType, final Class<?> boxedType, boolean allowShowGraph, final Set<Class<?>> typeSet)
    {
    	Class<?> targetClass = simpleType != null? simpleType : boxedType; 
        s_typeNames.put(name, targetClass);

        if (simpleType != null)
        {
	        s_types.put(simpleType, name);
	        if (typeSet != null)
	        {
	        	typeSet.add(simpleType);
	        }
        }
        if (boxedType != null)
        {
	        s_types.put(boxedType, name);
	        if (typeSet != null)
	        {
	        	typeSet.add(boxedType);
	        }
        }
        s_allowShowGraphForName.put(name, allowShowGraph);
    }

    private DataTypes()
    {
    }

    public static String TypeToName(final Class<?> type)
    {
        final String ret = s_types.get(type);
        assert(ret != null);
        return ret;
    }
    
    public static String TypeToNameOrNull(final Class<?> type)
    {
        final String ret = s_types.get(type);
        return ret;
    }

    public static Class<?> NameToType(final String name)
    {
        Class<?> ret = s_typeNames.get(name);
        assert(ret != null);
        return ret;
    }
    
    public static boolean IsGraphAllowed(String name)
    {
    	assert(s_allowShowGraphForName.containsKey(name));
        return s_allowShowGraphForName.get(name);
    }

    public static boolean IsIntType(Class<?> type)
    {
        return s_intTypes.contains(type);
    }

    public static boolean IsFloatType(Class<?> type)
    {
        return s_floatTypes.contains(type);
    }
    
}
