package com.atguigu.gmall.task.scheduledTask;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling //开启定时任务
public class ScheduledTask {

    @Autowired
    private RabbitService rabbitService;
    //cron 定时任务的表达式
    //每天凌晨发送消息
//    0/30每隔30s执行一次
    @Scheduled(cron = "0/30 * * * * ?")
    public void taskActivity(){
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,MqConst.ROUTING_TASK_1,false);
    }

    /**
     * 每天下午18点执行
     */
//@Scheduled(cron = "0/35 * * * * ?")
    @Scheduled(cron = "0 0 18 * * ?")
    public void task18() {

        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK, MqConst.ROUTING_TASK_18, "");
    }

}
