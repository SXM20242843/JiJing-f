package com.scenic.ai.modules.app.user.mapper;

import com.scenic.ai.modules.app.user.entity.WechatBindInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WechatBindMapper {

    @Select("""
        SELECT COUNT(1)
        FROM tourist_user
        WHERE user_id = #{userId}
          AND deleted = 0
    """)
    int countUserByUserId(@Param("userId") String userId);

    @Select("""
        SELECT
          wechat_openid,
          wechat_unionid,
          wechat_nickname,
          wechat_avatar,
          has_bind_wechat
        FROM tourist_user
        WHERE user_id = #{userId}
          AND deleted = 0
        LIMIT 1
    """)
    @Results(id = "WechatBindInfoMap", value = {
            @Result(column = "wechat_openid", property = "wechatOpenid"),
            @Result(column = "wechat_unionid", property = "wechatUnionid"),
            @Result(column = "wechat_nickname", property = "wechatNickname"),
            @Result(column = "wechat_avatar", property = "wechatAvatar"),
            @Result(column = "has_bind_wechat", property = "hasBindWechat")
    })
    WechatBindInfo selectWechatBindInfo(@Param("userId") String userId);

    @Update("""
        UPDATE tourist_user
        SET wechat_openid = #{openid},
            wechat_unionid = #{unionid},
            wechat_nickname = #{nickname},
            wechat_avatar = #{avatar},
            wechat_bind_time = #{bindTime},
            has_bind_wechat = 1,
            updated_at = #{bindTime}
        WHERE user_id = #{userId}
          AND deleted = 0
    """)
    int updateWechatBind(
            @Param("userId") String userId,
            @Param("openid") String openid,
            @Param("unionid") String unionid,
            @Param("nickname") String nickname,
            @Param("avatar") String avatar,
            @Param("bindTime") LocalDateTime bindTime
    );
}
