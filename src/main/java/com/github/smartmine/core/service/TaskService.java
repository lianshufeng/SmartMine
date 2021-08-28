package com.github.smartmine.core.service;

import com.github.smartmine.core.conf.TaskConf;
import com.github.smartmine.core.helper.BehashHelper;
import com.github.smartmine.core.helper.NvidiaSmiHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class TaskService {

    @Autowired
    private TaskConf taskConf;

    @Autowired
    private NvidiaSmiHelper nvidiaSmiHelper;

    @Autowired
    private BehashHelper behashHelper;

    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2);


    @Autowired
    private void init(ApplicationContext applicationContext) {
        this.scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                task();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void task() {
        final NvidiaSmiHelper.GpuInfo gpuInfo = nvidiaSmiHelper.getGpuInfo();
        if (gpuInfo == null) {
            return;
        }
        log.debug("gpu -> {}", gpuInfo);


        //检查任务
        checkTask(gpuInfo);

    }


    /**
     * 检查任务
     *
     * @param gpuInfo
     */
    private synchronized void checkTask(NvidiaSmiHelper.GpuInfo gpuInfo) {
        this.behashHelper.update(getCanUsedGPu());
    }


    /**
     * 取出收益率
     *
     * @return
     */
    public synchronized int getCanUsedGPu() {
        return 100 - this.taskConf.getWaveGpu() - this.nvidiaSmiHelper.getGpuInfo().getGpu();
    }


}
