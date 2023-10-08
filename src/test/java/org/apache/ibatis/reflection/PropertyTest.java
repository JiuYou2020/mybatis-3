package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code @Author: } JiuYou2020
 * <br>
 * {@code @Date: } 2023/10/8
 * <br>
 * {@code @Description: }
 */
@SuppressWarnings("unused")
public class PropertyTest {

    public static class TestBean {
        private String name;
        private int age;

        public TestBean(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public TestBean() {

        }

        // Getters and setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    @Test
    public void testCopyBeanProperties() {
        // 创建源对象
        TestBean sourceBean = new TestBean();
        sourceBean.setName("John");
        sourceBean.setAge(30);

        // 创建目标对象
        TestBean destinationBean = new TestBean();

        // 调用 PropertyCopier 的 copyBeanProperties 方法复制属性
        PropertyCopier.copyBeanProperties(TestBean.class, sourceBean, destinationBean);

        // 断言属性复制是否成功
        assertEquals("John", destinationBean.getName());
        assertEquals(30, destinationBean.getAge());
    }

    @Test
    public void testPropertyNamer() {
        assertEquals("name", PropertyNamer.methodToProperty("getName"));
        assertEquals("name", PropertyNamer.methodToProperty("setName"));
        assertEquals("name", PropertyNamer.methodToProperty("isName"));
        assertTrue(PropertyNamer.isProperty("getName"));
        assertTrue(PropertyNamer.isProperty("setName"));
        assertTrue(PropertyNamer.isProperty("isName"));
        assertTrue(PropertyNamer.isGetter("getName"));
        assertTrue(PropertyNamer.isGetter("isName"));
        assertTrue(PropertyNamer.isSetter("setName"));
    }

    @Test
    public void testPropertyTokenizer() {
        PropertyTokenizer propertyTokenizer = new PropertyTokenizer("order[0].item[0].name");
        assertEquals("order", propertyTokenizer.getName());
        assertEquals("0", propertyTokenizer.getIndex());
        assertEquals("order[0]", propertyTokenizer.getIndexedName());
        assertEquals("item[0].name", propertyTokenizer.getChildren());

        propertyTokenizer = propertyTokenizer.next();
        assertEquals("item", propertyTokenizer.getName());
        assertEquals("0", propertyTokenizer.getIndex());
        assertEquals("item[0]", propertyTokenizer.getIndexedName());
        assertEquals("name", propertyTokenizer.getChildren());

        propertyTokenizer = propertyTokenizer.next();
        assertNull(propertyTokenizer.getIndex());
        assertNull(propertyTokenizer.getChildren());
        assertEquals("name", propertyTokenizer.getName());
        assertEquals("name", propertyTokenizer.getIndexedName());
    }

}
