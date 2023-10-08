/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.Entry;

/**
 * 这个类的主要作用是通过反射去获取类的信息,比如类对应的class类型,类的可读属性,类的可写属性,类的构造方法,类的get方法,类的set方法,类的set方法的参数类型,类的get方法的返回值类型等等
 *
 * @author Clinton Begin
 */
public class Reflector {

    /**
     * 对应的类
     */
    private final Class<?> type;
    /**
     * 可读属性数组
     */
    private final String[] readablePropertyNames;
    /**
     * 可写属性集合
     */
    private final String[] writeablePropertyNames;
    /**
     * 属性对应的 setting 方法的映射。
     * <p>
     * key 为属性名称
     * value 为 Invoker 对象
     */
    private final Map<String, Invoker> setMethods = new HashMap<>();
    /**
     * 属性对应的 getting 方法的映射。
     * <p>
     * key 为属性名称
     * value 为 Invoker 对象
     */
    private final Map<String, Invoker> getMethods = new HashMap<>();
    /**
     * 属性对应的 setting 方法的方法参数类型的映射。{@link #setMethods}
     * <p>
     * key 为属性名称
     * value 为方法参数类型
     */
    private final Map<String, Class<?>> setTypes = new HashMap<>();
    /**
     * 属性对应的 getting 方法的返回值类型的映射。{@link #getMethods}
     * <p>
     * key 为属性名称
     * value 为返回值的类型
     */
    private final Map<String, Class<?>> getTypes = new HashMap<>();
    /**
     * 默认构造方法
     */
    private Constructor<?> defaultConstructor;
    /**
     * 不区分大小写的属性集合
     */
    private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

    /**
     * 构造方法,每个 Reflector 对象对应一个类,Reflector 对象会缓存反射操作需要的类的元信息,例如构造方法,属性名,setting/getting 方法等
     *
     * @param clazz 类对象
     */
    public Reflector(Class<?> clazz) {
        // 设置对应的类
        type = clazz;
        // <1> 初始化 defaultConstructor
        addDefaultConstructor(clazz);
        // <2> // 初始化 getMethods 和 getTypes ，通过遍历 getting 方法
        addGetMethods(clazz);
        // <3> // 初始化 setMethods 和 setTypes ，通过遍历 setting 方法。
        addSetMethods(clazz);
        // <4> // 初始化 getMethods + getTypes 和 setMethods + setTypes ，通过遍历 fields 属性。
        addFields(clazz);
        // <5> 初始化 readablePropertyNames、writeablePropertyNames、caseInsensitivePropertyMap 属性
        readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
        writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
        for (String propName : readablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
        for (String propName : writeablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
    }

    /**
     * 首先尝试获取系统安全管理器（SecurityManager），然后检查是否存在。如果存在安全管理器，它会尝试检查是否具有 suppressAccessChecks 权限。如果没有这个权限，checkPermission 会抛出 SecurityException，代码会捕获这个异常，并返回 false 表示没有足够的权限来控制成员的访问性。
     * <p>
     * 如果没有出现异常，意味着代码拥有足够的权限，可以控制成员的访问性，于是返回 true。
     * <p>
     * 这段代码的作用是在特定情况下检查代码是否可以绕过访问性限制，以便访问私有成员。通常情况下，这种权限检查在使用 Java 反射来访问私有成员时会用到。
     *
     * @return If can control member accessible, it return {@literal true}
     * @since 3.5.0
     */
    public static boolean canControlMemberAccessible() {
        try {
            SecurityManager securityManager = System.getSecurityManager();
            if (null != securityManager) {
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    /**
     * 初始化 defaultConstructor,通过反射获取所有构造方法，然后遍历，找到无参构造方法
     *
     * @param clazz 类对象
     */
    private void addDefaultConstructor(Class<?> clazz) {
        Constructor<?>[] consts = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : consts) {
            if (constructor.getParameterTypes().length == 0) {
                this.defaultConstructor = constructor;
            }
        }
    }

    /**
     * 通过遍历 getting 方法，初始化 getMethods 和 getTypes
     *
     * @param cls 类对象
     */
    private void addGetMethods(Class<?> cls) {
        // <1> 属性与其 getting 方法的映射。
        Map<String, List<Method>> conflictingGetters = new HashMap<>();
        // <2> 获得所有方法
        Method[] methods = getClassMethods(cls);
        // <3> 遍历所有方法
        for (Method method : methods) {
            // <3.1> 参数大于 0 ，说明不是 getting 方法，忽略
            if (method.getParameterTypes().length > 0) {
                continue;
            }
            // <3.2> 以 get 和 is 方法名开头，说明是 getting 方法
            String name = method.getName();
            if ((name.startsWith("get") && name.length() > 3)
                    || (name.startsWith("is") && name.length() > 2)) {
                //todo <3.3> 获得属性
                name = PropertyNamer.methodToProperty(name);
                // <3.4> 添加到 conflictingGetters 中
                addMethodConflict(conflictingGetters, name, method);
            }
        }
        //todo <4> 解决 getting 冲突方法
        resolveGetterConflicts(conflictingGetters);
    }

    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        // 遍历每个属性，查找其最匹配的方法。因为子类可以覆写父类的方法，所以一个属性，可能对应多个 getting 方法
        for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
            Method winner = null; // 最匹配的方法
            String propName = entry.getKey();
            for (Method candidate : entry.getValue()) {
                // winner 为空，说明 candidate 为最匹配的方法
                if (winner == null) {
                    winner = candidate;
                    continue;
                }
                // <1> 基于返回类型比较
                Class<?> winnerType = winner.getReturnType();
                Class<?> candidateType = candidate.getReturnType();
                // 类型相同
                if (candidateType.equals(winnerType)) {
                    // 返回值了诶选哪个相同，应该在 getClassMethods 方法中，已经合并。所以抛出 ReflectionException 异常
                    if (!boolean.class.equals(candidateType)) {
                        throw new ReflectionException(
                                "Illegal overloaded getter method with ambiguous type for property "
                                        + propName + " in class " + winner.getDeclaringClass()
                                        + ". This breaks the JavaBeans specification and can cause unpredictable results.");
                        // 选择 boolean 类型的 is 方法
                    } else if (candidate.getName().startsWith("is")) {
                        winner = candidate;
                    }
                    // 不符合选择子类
                } else if (candidateType.isAssignableFrom(winnerType)) {
                    // OK getter type is descendant
                    // <1.1> 符合选择子类。因为子类可以修改放大返回值。例如，父类的一个方法的返回值为 List ，子类对该方法的返回值可以覆写为 ArrayList 。
                } else if (winnerType.isAssignableFrom(candidateType)) {
                    winner = candidate;
                    // <1.2> 返回类型冲突，抛出 ReflectionException 异常
                } else {
                    throw new ReflectionException(
                            "Illegal overloaded getter method with ambiguous type for property "
                                    + propName + " in class " + winner.getDeclaringClass()
                                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
                }
            }
            // <2> 添加到 getMethods 和 getTypes 中
            addGetMethod(propName, winner);
        }
    }

    private void addGetMethod(String name, Method method) {
        // 判断是合理的属性名
        if (isValidPropertyName(name)) {
            // 添加到 getMethods 中
            getMethods.put(name, new MethodInvoker(method));
            // 添加到 getTypes 中
            Type returnType = TypeParameterResolver.resolveReturnType(method, type);
            getTypes.put(name, typeToClass(returnType));
        }
    }

    private void addSetMethods(Class<?> cls) {
        // 属性与其 setting 方法的映射。
        Map<String, List<Method>> conflictingSetters = new HashMap<>();
        // 获得所有方法
        Method[] methods = getClassMethods(cls);
        // 遍历所有方法
        for (Method method : methods) {
            String name = method.getName();
            // 方法名为 set 开头
            // 参数数量为 1
            if (name.startsWith("set") && name.length() > 3) {
                if (method.getParameterTypes().length == 1) {
                    // 获得属性
                    name = PropertyNamer.methodToProperty(name);
                    // 添加到 conflictingSetters 中
                    addMethodConflict(conflictingSetters, name, method);
                }
            }
        }
        // 解决 setting 冲突方法
        resolveSetterConflicts(conflictingSetters);
    }

    private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
        List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
        list.add(method);
    }

    private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
        // 遍历每个属性，查找其最匹配的方法。因为子类可以覆写父类的方法，所以一个属性，可能对应多个 setting 方法
        for (String propName : conflictingSetters.keySet()) {
            List<Method> setters = conflictingSetters.get(propName);
            Class<?> getterType = getTypes.get(propName);
            Method match = null;
            ReflectionException exception = null;
            // 遍历属性对应的 setting 方法
            for (Method setter : setters) {
                Class<?> paramType = setter.getParameterTypes()[0];
                // 和 getterType 相同，直接使用
                if (paramType.equals(getterType)) {
                    // should be the best match
                    match = setter;
                    break;
                }
                if (exception == null) {
                    try {
                        // 选择一个更加匹配的
                        match = pickBetterSetter(match, setter, propName);
                    } catch (ReflectionException e) {
                        // there could still be the 'best match'
                        match = null;
                        exception = e;
                    }
                }
            }
            // 添加到 setMethods 和 setTypes 中
            if (match == null) {
                throw exception;
            } else {
                addSetMethod(propName, match);
            }
        }
    }

    private Method pickBetterSetter(Method setter1, Method setter2, String property) {
        if (setter1 == null) {
            return setter2;
        }
        Class<?> paramType1 = setter1.getParameterTypes()[0];
        Class<?> paramType2 = setter2.getParameterTypes()[0];
        if (paramType1.isAssignableFrom(paramType2)) {
            return setter2;
        } else if (paramType2.isAssignableFrom(paramType1)) {
            return setter1;
        }
        throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
                + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
                + paramType2.getName() + "'.");
    }

    private void addSetMethod(String name, Method method) {
        if (isValidPropertyName(name)) {
            // 添加到 setMethods 中
            setMethods.put(name, new MethodInvoker(method));
            // 添加到 setTypes 中
            Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
            setTypes.put(name, typeToClass(paramTypes[0]));
        }
    }

    private Class<?> typeToClass(Type src) {
        Class<?> result = null;
        // 普通类型，直接使用类
        if (src instanceof Class) {
            result = (Class<?>) src;
            // 泛型类型，使用泛型
        } else if (src instanceof ParameterizedType) {
            result = (Class<?>) ((ParameterizedType) src).getRawType();
            // 泛型数组，获得具体类
        } else if (src instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) src).getGenericComponentType();
            if (componentType instanceof Class) { // 普通类型
                result = Array.newInstance((Class<?>) componentType, 0).getClass();
            } else {
                Class<?> componentClass = typeToClass(componentType); // 递归该方法，返回类
                result = Array.newInstance(componentClass, 0).getClass();
            }
        }
        // 都不符合，使用 Object 类
        if (result == null) {
            result = Object.class;
        }
        return result;
    }

    /**
     * 通过遍历 fields 属性，初始化 getMethods + getTypes 和 setMethods + setTypes,
     * 为类的字段生成访问器方法（getter）和修改器方法（setter），以及收集字段类型信息。
     *
     * @param clazz 类对象
     */
    private void addFields(Class<?> clazz) {
        // 获得所有 field 们
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (!setMethods.containsKey(field.getName())) {
                //问题 379 - 删除了对 Final 的检查，因为 JDK 1.5 允许通过反射修改 Final 字段（JSR-133）。 (JGB) pr 16 - 最终静态只能由类加载器设置
                int modifiers = field.getModifiers();
                if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
                    addSetField(field);
                }
            }
            // 添加到 getMethods 和 getTypes 中
            if (!getMethods.containsKey(field.getName())) {
                addGetField(field);
            }
        }
        // 递归，处理父类
        if (clazz.getSuperclass() != null) {
            addFields(clazz.getSuperclass());
        }
    }

    private void addSetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            setMethods.put(field.getName(), new SetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            setTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addGetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            getMethods.put(field.getName(), new GetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            getTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private boolean isValidPropertyName(String name) {
        return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
    }

    /**
     * 此方法返回一个数组，其中包含此类和任何超类(该类的所有父类,包括直接父类,父类的父类...)中声明的所有方法。我们使用此方法，而不是更简单的 Class.getMethods()，因为我们也想查找私有方法.
     *
     * @param cls class对象
     * @return 包含此类中所有方法的数组
     */
    private Method[] getClassMethods(Class<?> cls) {
        // 每个方法签名与该方法的映射
        Map<String, Method> uniqueMethods = new HashMap<>();
        // 循环类，类的父类，类的父类的父类，直到父类为 Object
        Class<?> currentClass = cls;
        while (currentClass != null && currentClass != Object.class) {
            // <1> 记录当前类定义的方法
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

            // we also need to look for interface methods -
            // because the class may be abstract
            // <2> 记录接口中定义的方法
            Class<?>[] interfaces = currentClass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                addUniqueMethods(uniqueMethods, anInterface.getMethods());
            }

            // 获得父类
            currentClass = currentClass.getSuperclass();
        }

        // 转换成 Method 数组返回
        Collection<Method> methods = uniqueMethods.values();

        return methods.toArray(new Method[methods.size()]);
    }

    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method currentMethod : methods) {
            //  忽略 bridge 方法，参见 https://www.zhihu.com/question/54895701/answer/141623158 文章
            if (!currentMethod.isBridge()) {
                // <3> 获得方法签名
                String signature = getSignature(currentMethod);
                //检查该方法是否已知，如果已知，则扩展类必须重写该方法
                // 当 uniqueMethods 不存在时，进行添加
                if (!uniqueMethods.containsKey(signature)) {
                    // 添加到 uniqueMethods 中
                    uniqueMethods.put(signature, currentMethod);
                }
            }
        }
    }

    /**
     * 获得方法签名。
     * <p>
     * 格式：returnType#方法名:参数名1,参数名2,参数名3
     * 例如：void#checkPackageAccess:java.lang.ClassLoader,boolean
     *
     * @param method 方法
     * @return 方法签名
     */
    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        // 返回类型
        Class<?> returnType = method.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getName()).append('#');
        }
        // 方法名
        sb.append(method.getName());
        // 方法参数
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (i == 0) {
                sb.append(':');
            } else {
                sb.append(',');
            }
            sb.append(parameters[i].getName());
        }
        return sb.toString();
    }

    /**
     * Gets the name of the class the instance provides information for
     *
     * @return The class name
     */
    public Class<?> getType() {
        return type;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor != null) {
            return defaultConstructor;
        } else {
            throw new ReflectionException("There is no default constructor for " + type);
        }
    }

    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }

    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    /**
     * Gets the type for a property setter
     *
     * @param propertyName - the name of the property
     * @return The Class of the property setter
     */
    public Class<?> getSetterType(String propertyName) {
        Class<?> clazz = setTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets the type for a property getter
     *
     * @param propertyName - the name of the property
     * @return The Class of the property getter
     */
    public Class<?> getGetterType(String propertyName) {
        Class<?> clazz = getTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets an array of the readable properties for an object
     *
     * @return The array
     */
    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    /**
     * Gets an array of the writable properties for an object
     *
     * @return The array
     */
    public String[] getSetablePropertyNames() {
        return writeablePropertyNames;
    }

    /**
     * Check to see if a class has a writable property by name
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a writable property by the name
     */
    public boolean hasSetter(String propertyName) {
        return setMethods.keySet().contains(propertyName);
    }

    /**
     * Check to see if a class has a readable property by name
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a readable property by the name
     */
    public boolean hasGetter(String propertyName) {
        return getMethods.keySet().contains(propertyName);
    }

    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
    }
}
