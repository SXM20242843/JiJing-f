package com.scenic.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.scenic.ai.modules.app.mapper",
        "com.scenic.ai.modules.app.user.mapper",
        "com.scenic.ai.modules.app.visit.mapper",
        "com.scenic.ai.modules.app.route.mapper",
        "com.scenic.ai.modules.app.payment.mapper",
        "com.scenic.ai.modules.chat.mapper"
})
public class ScenicAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScenicAiApplication.class, args);
    }
}
