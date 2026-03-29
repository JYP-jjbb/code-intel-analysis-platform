package com.mickey.onlineordering.onlineorderingserver.mapper;

import com.mickey.onlineordering.onlineorderingserver.entity.CustomerServiceMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客服消息Mapper接口
 */
@Mapper
public interface CustomerServiceMessageMapper {

    int insert(CustomerServiceMessage message);
    List<CustomerServiceMessage> selectBySessionId(@Param("sessionId") String sessionId);
    List<CustomerServiceMessage> selectByUserId(@Param("userId") Long userId);
    int markAsRead(@Param("sessionId") String sessionId, @Param("receiverId") Long receiverId);
    int countUnreadMessages(@Param("receiverId") Long receiverId);
    List<String> selectActiveSessions();
    int countUnreadMessagesBySession(@Param("sessionId") String sessionId, @Param("receiverId") Long receiverId);
}

