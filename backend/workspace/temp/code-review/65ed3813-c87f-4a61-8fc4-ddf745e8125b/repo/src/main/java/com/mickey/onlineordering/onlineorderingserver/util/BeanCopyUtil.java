package com.mickey.onlineordering.onlineorderingserver.util;

import cn.hutool.core.bean.BeanUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean拷贝工具类
 * 用于DTO、Entity、VO之间的对象转换
 */
public class BeanCopyUtil {
    
    /**
     * 单个对象拷贝
     *
     * @param source 源对象
     * @param targetClass 目标类
     * @return 目标对象
     */
    public static <T> T copyBean(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        return BeanUtil.copyProperties(source, targetClass);
    }
    
    /**
     * 列表对象拷贝
     *
     * @param sourceList 源列表
     * @param targetClass 目标类
     * @return 目标列表
     */
    public static <T> List<T> copyList(List<?> sourceList, Class<T> targetClass) {
        if (sourceList == null || sourceList.isEmpty()) {
            return List.of();
        }
        return sourceList.stream()
                .map(item -> copyBean(item, targetClass))
                .collect(Collectors.toList());
    }
    
    private BeanCopyUtil() {
        // 私有构造函数，防止实例化
    }
}











