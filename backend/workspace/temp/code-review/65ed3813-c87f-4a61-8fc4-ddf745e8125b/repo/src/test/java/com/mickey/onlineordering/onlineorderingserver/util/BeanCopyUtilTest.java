package com.mickey.onlineordering.onlineorderingserver.util;

import com.mickey.onlineordering.onlineorderingserver.entity.User;
import com.mickey.onlineordering.onlineorderingserver.vo.UserProfileVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bean拷贝工具类测试
 * 测试对象属性拷贝功能
 */
@DisplayName("Bean拷贝工具类测试")
class BeanCopyUtilTest {
    
    @Test
    @DisplayName("测试拷贝单个对象 - 成功")
    void testCopyBean_WithValidSource_ReturnTarget() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPhone("13800138000");
        user.setRole("user");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        
        // When
        UserProfileVo vo = BeanCopyUtil.copyBean(user, UserProfileVo.class);
        
        // Then
        assertNotNull(vo);
        assertEquals(user.getId(), vo.getId());
        assertEquals(user.getUsername(), vo.getUsername());
        assertEquals(user.getEmail(), vo.getEmail());
        assertEquals(user.getPhone(), vo.getPhone());
    }
    
    @Test
    @DisplayName("测试拷贝单个对象 - 源对象为null")
    void testCopyBean_WithNullSource_ReturnNull() {
        // When
        UserProfileVo vo = BeanCopyUtil.copyBean(null, UserProfileVo.class);
        
        // Then
        assertNull(vo, "源对象为null时应返回null");
    }
    
    @Test
    @DisplayName("测试拷贝对象列表 - 成功")
    void testCopyBeanList_WithValidList_ReturnTargetList() {
        // Given
        List<User> users = new ArrayList<>();
        
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("user1");
        users.add(user1);
        
        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("user2");
        users.add(user2);
        
        // When
        List<UserProfileVo> voList = BeanCopyUtil.copyList(users, UserProfileVo.class);
        
        // Then
        assertNotNull(voList);
        assertEquals(2, voList.size());
        assertEquals("user1", voList.get(0).getUsername());
        assertEquals("user2", voList.get(1).getUsername());
    }
    
    @Test
    @DisplayName("测试拷贝对象列表 - 空列表")
    void testCopyBeanList_WithEmptyList_ReturnEmptyList() {
        // Given
        List<User> users = new ArrayList<>();
        
        // When
        List<UserProfileVo> voList = BeanCopyUtil.copyList(users, UserProfileVo.class);
        
        // Then
        assertNotNull(voList);
        assertTrue(voList.isEmpty());
    }
    
    @Test
    @DisplayName("测试拷贝对象列表 - null列表")
    void testCopyBeanList_WithNullList_ReturnEmptyList() {
        // When
        List<UserProfileVo> voList = BeanCopyUtil.copyList(null, UserProfileVo.class);
        
        // Then
        assertNotNull(voList);
        assertTrue(voList.isEmpty());
    }
    
    @Test
    @DisplayName("测试属性名不匹配 - 只拷贝匹配的属性")
    void testCopyBean_WithMismatchedProperties_CopyMatchedOnly() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encrypted_password");  // UserProfileVo中没有password
        
        // When
        UserProfileVo vo = BeanCopyUtil.copyBean(user, UserProfileVo.class);
        
        // Then
        assertNotNull(vo);
        assertEquals(user.getUsername(), vo.getUsername());
        // password不应被拷贝（UserProfileVo中没有该字段）
    }
    
    @Test
    @DisplayName("测试深拷贝 - 修改源对象不影响目标对象")
    void testCopyBean_DeepCopy_IndependentObjects() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setUsername("original");
        
        // When
        UserProfileVo vo = BeanCopyUtil.copyBean(user, UserProfileVo.class);
        
        // 修改源对象
        user.setUsername("modified");
        
        // Then: 目标对象不应受影响
        assertEquals("original", vo.getUsername());
    }
    
    @Test
    @DisplayName("测试拷贝大量对象 - 性能测试")
    void testCopyBeanList_WithLargeList_PerformanceTest() {
        // Given: 创建10000个对象
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            User user = new User();
            user.setId((long) i);
            user.setUsername("user" + i);
            users.add(user);
        }
        
        // When: 记录拷贝时间
        long startTime = System.currentTimeMillis();
        List<UserProfileVo> voList = BeanCopyUtil.copyList(users, UserProfileVo.class);
        long endTime = System.currentTimeMillis();
        
        // Then
        assertEquals(10000, voList.size());
        long duration = endTime - startTime;
        assertTrue(duration < 1000, "拷贝10000个对象应在1秒内完成，实际耗时：" + duration + "ms");
    }
    
    @Test
    @DisplayName("测试拷贝null属性 - 不覆盖目标对象的非null值")
    void testCopyBean_WithNullProperties_HandleCorrectly() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail(null);  // email为null
        
        // When
        UserProfileVo vo = BeanCopyUtil.copyBean(user, UserProfileVo.class);
        
        // Then
        assertNotNull(vo);
        assertEquals("testuser", vo.getUsername());
        // email为null的处理取决于BeanCopyUtil的实现
    }
}

