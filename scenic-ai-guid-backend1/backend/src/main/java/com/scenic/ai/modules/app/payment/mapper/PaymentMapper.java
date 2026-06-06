package com.scenic.ai.modules.app.payment.mapper;

import com.scenic.ai.modules.app.payment.dto.PaymentRecordDto;
import com.scenic.ai.modules.app.payment.dto.PaymentShopInfoDto;
import com.scenic.ai.modules.app.payment.entity.PaymentRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentMapper {

    @Select("""
        SELECT
          #{shopCode} AS shop_code,
          id AS merchant_id,
          facility_name AS merchant_name,
          facility_code AS location_id,
          CASE
            WHEN UPPER(TRIM(facility_type)) IN ('TICKET', 'TICKET_OFFICE', 'TICKET-OFFICE')
              OR TRIM(facility_type) IN ('门票', '票务', '售票', '售票处')
              THEN 'ticket'

            WHEN UPPER(TRIM(facility_type)) IN ('FOOD', 'RESTAURANT', 'DINING')
              OR TRIM(facility_type) IN ('餐饮', '餐厅', '饭店', '美食', '素斋')
              THEN 'food'

            WHEN UPPER(TRIM(facility_type)) IN ('SHOP', 'STORE', 'SHOPPING', 'RETAIL')
              OR TRIM(facility_type) IN ('购物', '商店', '文创', '零售')
              THEN 'shopping'

            WHEN UPPER(TRIM(facility_type)) IN ('TRANSPORT', 'PARKING', 'BUS')
              OR TRIM(facility_type) IN ('交通', '停车', '停车场', '观光车', '摆渡车')
              THEN 'transport'

            WHEN UPPER(TRIM(facility_type)) IN ('HOTEL', 'ACCOMMODATION', 'LODGE')
              OR TRIM(facility_type) IN ('酒店', '住宿', '客栈', '民宿')
              THEN 'accommodation'

            ELSE 'entertainment'
          END AS consumption_type
        FROM scenic_facility
        WHERE deleted = 0
          AND status = 1
          AND (
            facility_code = #{shopCode}
            OR payment_code = #{shopCode}
            OR map_poi_id = #{shopCode}
            OR CONCAT('P-', id) = #{shopCode}
            OR CAST(id AS CHAR) = #{shopCode}
          )
        LIMIT 1
    """)
    @Results(id = "paymentShopInfoMap", value = {
            @Result(column = "shop_code", property = "shopCode"),
            @Result(column = "merchant_id", property = "merchantId"),
            @Result(column = "merchant_name", property = "merchantName"),
            @Result(column = "location_id", property = "locationId"),
            @Result(column = "consumption_type", property = "consumptionType")
    })
    PaymentShopInfoDto selectShopInfoByShopCode(@Param("shopCode") String shopCode);

    @Insert("""
        INSERT INTO payment_record (
          user_id,
          wechat_openid,
          consumption_type,
          amount,
          payment_id,
          merchant_name,
          location_id,
          status,
          pay_time,
          merchant_id
        ) VALUES (
          #{userId},
          #{wechatOpenid},
          #{consumptionType},
          #{amount},
          #{paymentId},
          #{merchantName},
          #{locationId},
          #{status},
          #{payTime},
          #{merchantId}
        )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPaymentRecord(PaymentRecord record);

    @Select("""
        SELECT
          id,
          user_id,
          consumption_type,
          amount,
          payment_id,
          merchant_name,
          location_id,
          status,
          pay_time,
          merchant_id
        FROM payment_record
        WHERE user_id = #{userId}
        ORDER BY pay_time DESC, id DESC
    """)
    @Results(id = "paymentRecordDtoMap", value = {
            @Result(column = "id", property = "id"),
            @Result(column = "user_id", property = "user_id"),
            @Result(column = "consumption_type", property = "consumption_type"),
            @Result(column = "amount", property = "amount"),
            @Result(column = "payment_id", property = "payment_id"),
            @Result(column = "merchant_name", property = "merchant_name"),
            @Result(column = "location_id", property = "location_id"),
            @Result(column = "status", property = "status"),
            @Result(column = "pay_time", property = "pay_time"),
            @Result(column = "merchant_id", property = "merchant_id")
    })
    List<PaymentRecordDto> selectRecordsByUserId(@Param("userId") String userId);
}