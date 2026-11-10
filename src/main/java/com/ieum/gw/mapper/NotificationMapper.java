package com.ieum.gw.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ieum.gw.dto.NotificationDTO;
import com.ieum.gw.dto.NotificationSaveDTO;
import com.ieum.gw.dto.NotificationUnreadCountDTO;

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

    List<NotificationUnreadCountDTO> countUnreadSupportReplies(@Param("companyId") Long companyId,
                                                                @Param("userAccountId") Long userAccountId);

    int markSupportRepliesRead(@Param("companyId") Long companyId,
                               @Param("userAccountId") Long userAccountId,
                               @Param("supportInquiryId") Long supportInquiryId);

    int countOwned(@Param("companyId") Long companyId,
                   @Param("userAccountId") Long userAccountId,
                   @Param("notificationId") Long notificationId);
}
