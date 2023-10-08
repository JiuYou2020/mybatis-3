package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code @Author: } JiuYou2020
 * <br>
 * {@code @Date: } 2023/10/7
 * <br>
 * {@code @Description: }
 */
public class InvokerTest {

    @Test
    public void testGetFieldInvoker() throws NoSuchFieldException, IllegalAccessException {
        Person person = new Person();
        person.setName("JiuYou");

        Field field = Person.class.getDeclaredField("name");
        GetFieldInvoker invoker = new GetFieldInvoker(field);
        assertEquals("JiuYou", invoker.invoke(person, null));
    }

    @Test
    public void testSetFieldInvoker() throws NoSuchFieldException, IllegalAccessException {
        Person person = new Person();
        Field field = Person.class.getDeclaredField("name");
        SetFieldInvoker invoker = new SetFieldInvoker(field);
        invoker.invoke(person, new Object[]{"JiuYou"});
        assertEquals("JiuYou", person.getName());
    }

    @Test
    public void testMethodInvoker() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Person person = new Person();
        person.setName("John");
        Method method = Person.class.getDeclaredMethod("getName");
        MethodInvoker invoker = new MethodInvoker(method);
        assertEquals("John", invoker.invoke(person, null));
    }


    static class Person {
        String name;

        public Person() {
        }

        public Person(String name, int age) {
            this.name = name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}
