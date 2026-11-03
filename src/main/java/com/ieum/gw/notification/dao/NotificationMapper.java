package com.ieum.gw.notification.dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.notification.dto.NotificationDTO;
import com.ieum.gw.notification.dto.NotificationSaveDTO;

@Mapper
public interface NotificationMapper {

    Long findAccountId(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId);

    int insertNotification(NotificationSaveDTO dto);

    List<NotificationDTO> listNotifications(@Param("companyId") Long companyId,
                                            @Param("userAccountId") Long userAccountId,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    int countNotifications(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId);

    int countUnread(@Param("companyId") Long companyId, @Param("userAccountId") Long userAccountId);

    int markRead(@Param("companyId") Long companyId,
                 @Param("userAccountId") Long userAccountId,
                 @Param("notificationId") Long notificationId);

    int countOwned(@Param("companyId") Long companyId,
                   @Param("userAccountId") Long userAccountId,
                   @Param("notificationId") Long notificationId);
}
