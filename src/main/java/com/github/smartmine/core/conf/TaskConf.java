package com.github.smartmine.core.conf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.File;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Component
@ConfigurationProperties(prefix = "task")
public class TaskConf {

    //工具
    private File nvidiaSmiPath;

    //捕获时间,单位秒, 时间间隔越长， GPU约平稳
    private int sampleTime = 15;

    //触发挖矿的延迟时间(秒)
    private int keepTaskStatTime = 60;

    // 最小收益率
    private int minIncomeGpu = 15;

    //保持收益增加时间（进行调整）
    private int keepAddTime = 180;

    //保持收益减少时间（进行调整）
    private int keepSubTime = 20;


    //波动的gpu
    private int waveGpu = 5;

    //波动的显存
    private int waveMemory = 5;

}
